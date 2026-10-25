package com.airchess.server

import com.airchess.protocol.CameraSignalKind
import com.airchess.protocol.ClientMessage
import com.airchess.protocol.ErrorCode
import com.airchess.protocol.GameResult
import com.airchess.protocol.GameSnapshot
import com.airchess.protocol.ResultReason
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.Side
import com.airchess.protocol.TimeControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val ABANDON_GRACE_MS = 60_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_CAMERA_PAYLOAD = 48 * 1024

internal class Seat(val name: String, val token: String, var side: Side) {
    var connection: Connection? = null
    var graceJob: Job? = null
    var cameraReady = false
}

/**
 * One game between two seats. All state is guarded by [lock]; timers re-enter through it.
 * Clocks only start once both sides have moved, so the first move of each side is untimed.
 */
internal class GameRoom(
    val code: String,
    private val timeControl: TimeControl,
    private val creatorSide: Side,
    private val newReferee: () -> MoveReferee,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val newToken: () -> String,
    private val graceMs: Long = ABANDON_GRACE_MS,
) {
    private val lock = Mutex()
    private val seats = mutableListOf<Seat>()
    private val moves = mutableListOf<String>()
    private val remaining = LongArray(Side.entries.size)
    private var referee = newReferee()
    private var gameNumber = 1
    private var tickAt = 0L
    private var result: GameResult? = null
    private var drawOfferBy: Side? = null
    private var rematchOfferBy: Side? = null
    private var flagJob: Job? = null
    private var flagGeneration = 0
    private var lastActivity = now()

    init {
        resetClocks()
    }

    private val started get() = seats.size == 2
    private val inProgress get() = started && result == null
    private val sideToMove get() = if (moves.size % 2 == 0) Side.WHITE else Side.BLACK
    private val clockRunning get() = inProgress && moves.size >= 2

    /** Seats the next player, or returns null when the room is full. */
    suspend fun join(name: String, conn: Connection): Seat? = lock.withLock {
        val side = when (seats.size) {
            0 -> creatorSide
            1 -> creatorSide.opposite
            else -> return null
        }
        val seat = Seat(name, newToken(), side)
        seats += seat
        attach(seat, conn)
        seats.filter { it !== seat && it.connection == null }.forEach { armGrace(it) }
        seat.send(joined(seat))
        broadcast(except = seat)
        seat
    }

    /** Re-attaches the seat owning [token], or returns null for an unknown token. */
    suspend fun resume(token: String, conn: Connection): Seat? = lock.withLock {
        val seat = seats.firstOrNull { it.token == token } ?: return null
        attach(seat, conn)
        seat.send(joined(seat))
        broadcast(except = seat)
        seat
    }

    suspend fun disconnect(seat: Seat, conn: Connection) = lock.withLock {
        if (seat.connection !== conn) {
            return@withLock
        }
        detach(seat)
        stopCamera(seat)
        broadcast()
    }

    suspend fun handle(seat: Seat, conn: Connection, message: ClientMessage) = lock.withLock {
        if (seat.connection !== conn) {
            return@withLock
        }
        touch()
        when (message) {
            is ClientMessage.MakeMove -> move(seat, message)
            ClientMessage.Resign -> resign(seat)
            ClientMessage.OfferDraw -> offerDraw(seat)
            is ClientMessage.RespondDraw -> respondDraw(seat, message.accept)
            ClientMessage.OfferRematch -> offerRematch(seat)
            is ClientMessage.RespondRematch -> respondRematch(seat, message.accept)
            ClientMessage.Leave -> leave(seat)
            is ClientMessage.CameraSignal -> signalCamera(seat, message)
            else -> seat.fail(ErrorCode.BAD_REQUEST, "Already in a game")
        }
    }

    suspend fun isIdle(ttlMs: Long): Boolean = lock.withLock {
        seats.none { it.connection != null } && now() - lastActivity > ttlMs
    }

    suspend fun close() = lock.withLock {
        flagJob?.cancel()
        seats.forEach { it.graceJob?.cancel() }
    }

    private suspend fun signalCamera(seat: Seat, signal: ClientMessage.CameraSignal) {
        if (signal.payload.length > MAX_CAMERA_PAYLOAD) {
            seat.fail(ErrorCode.BAD_REQUEST, "Camera signal too large")
            return
        }
        val other = seats.firstOrNull { it !== seat }
        when (signal.kind) {
            CameraSignalKind.STOP -> {
                stopCamera(seat)
                return
            }
            CameraSignalKind.READY -> {
                seat.cameraReady = true
                if (other?.cameraReady == true) {
                    seat.send(ServerMessage.CameraSignal(CameraSignalKind.READY))
                }
            }
            else -> {
                if (!seat.cameraReady || other?.cameraReady != true) {
                    seat.fail(ErrorCode.BAD_REQUEST, "Both players must enable their cameras")
                    return
                }
            }
        }
        other?.send(ServerMessage.CameraSignal(signal.kind, signal.payload))
    }

    private suspend fun stopCamera(seat: Seat) {
        val other = seats.firstOrNull { it !== seat }
        if (!seat.cameraReady && other?.cameraReady != true) {
            return
        }
        seat.cameraReady = false
        other?.cameraReady = false
        other?.send(ServerMessage.CameraSignal(CameraSignalKind.STOP))
    }

    private suspend fun move(seat: Seat, message: ClientMessage.MakeMove) {
        val error = when {
            !started -> ErrorCode.BAD_REQUEST
            result != null -> ErrorCode.GAME_OVER
            seat.side != sideToMove -> ErrorCode.NOT_YOUR_TURN
            message.ply != moves.size -> ErrorCode.BAD_REQUEST
            else -> null
        }
        if (error != null) {
            seat.reject(message.uci, error)
            return
        }
        if (clockRunning && remaining(seat.side) <= 0) {
            finish(timeoutResult(seat.side))
            broadcast()
            seat.reject(message.uci, ErrorCode.GAME_OVER)
            return
        }
        val outcome = referee.play(message.uci)
        if (outcome !is MoveOutcome.Legal) {
            seat.reject(message.uci, ErrorCode.ILLEGAL_MOVE)
            return
        }
        val time = now()
        if (clockRunning) {
            remaining[seat.side.ordinal] = remaining(seat.side) + timeControl.incrementSeconds * MILLIS_PER_SECOND
        }
        moves += message.uci
        tickAt = time
        drawOfferBy = null
        if (outcome.result != null) {
            finish(outcome.result)
        } else {
            scheduleFlag()
        }
        broadcast()
    }

    private suspend fun resign(seat: Seat) {
        if (!requireInProgress(seat)) {
            return
        }
        finish(GameResult(seat.side.opposite, ResultReason.RESIGNATION))
        broadcast()
    }

    private suspend fun leave(seat: Seat) {
        stopCamera(seat)
        if (inProgress) {
            finish(GameResult(seat.side.opposite, ResultReason.ABANDONED))
        }
        detach(seat)
        broadcast()
    }

    private suspend fun offerDraw(seat: Seat) {
        if (!requireInProgress(seat)) {
            return
        }
        if (drawOfferBy != null) {
            seat.fail(ErrorCode.BAD_REQUEST, "A draw offer is already pending")
            return
        }
        drawOfferBy = seat.side
        broadcast()
    }

    private suspend fun respondDraw(seat: Seat, accept: Boolean) {
        if (!requireInProgress(seat)) {
            return
        }
        if (drawOfferBy != seat.side.opposite) {
            seat.fail(ErrorCode.BAD_REQUEST, "No draw offer to answer")
            return
        }
        if (accept) {
            finish(GameResult(null, ResultReason.DRAW_AGREED))
        } else {
            drawOfferBy = null
        }
        broadcast()
    }

    private suspend fun offerRematch(seat: Seat) {
        if (result == null || rematchOfferBy != null) {
            seat.fail(ErrorCode.BAD_REQUEST, "Rematch not available")
            return
        }
        rematchOfferBy = seat.side
        broadcast()
    }

    private suspend fun respondRematch(seat: Seat, accept: Boolean) {
        if (result == null || rematchOfferBy != seat.side.opposite) {
            seat.fail(ErrorCode.BAD_REQUEST, "No rematch offer to answer")
            return
        }
        if (!accept) {
            rematchOfferBy = null
            broadcast()
            return
        }
        gameNumber++
        seats.forEach { it.side = it.side.opposite }
        referee = newReferee()
        moves.clear()
        resetClocks()
        result = null
        drawOfferBy = null
        rematchOfferBy = null
        seats.forEach { it.send(joined(it)) }
    }

    private suspend fun requireInProgress(seat: Seat): Boolean {
        if (inProgress) {
            return true
        }
        val code = if (result != null) ErrorCode.GAME_OVER else ErrorCode.BAD_REQUEST
        seat.fail(code, "No game in progress")
        return false
    }

    private suspend fun attach(seat: Seat, conn: Connection) {
        if (seat.connection != null) {
            stopCamera(seat)
        }
        seat.connection = conn
        seat.graceJob?.cancel()
        touch()
    }

    private fun detach(seat: Seat) {
        seat.connection = null
        touch()
        armGrace(seat)
    }

    private fun armGrace(seat: Seat) {
        if (!inProgress) {
            return
        }
        seat.graceJob?.cancel()
        seat.graceJob = scope.launch {
            delay(graceMs)
            lock.withLock {
                if (seat.connection != null || !inProgress) {
                    return@withLock
                }
                finish(GameResult(seat.side.opposite, ResultReason.ABANDONED))
                broadcast()
            }
        }
    }

    private fun scheduleFlag() {
        flagJob?.cancel()
        if (!clockRunning) {
            return
        }
        val generation = ++flagGeneration
        val side = sideToMove
        flagJob = scope.launch {
            delay(remaining(side))
            lock.withLock {
                if (generation != flagGeneration || result != null) {
                    return@withLock
                }
                flagJob = null
                finish(timeoutResult(side))
                broadcast()
            }
        }
    }

    /** A flag fall is only a draw when the opponent could never have mated. */
    private fun timeoutResult(flagged: Side): GameResult =
        if (referee.canMate(flagged.opposite)) {
            GameResult(flagged.opposite, ResultReason.TIMEOUT)
        } else {
            GameResult(null, ResultReason.INSUFFICIENT_MATERIAL)
        }

    /** Freezes the clocks at [now] and records the result. */
    private fun finish(outcome: GameResult) {
        if (clockRunning) {
            remaining[sideToMove.ordinal] = remaining(sideToMove)
        }
        tickAt = now()
        result = outcome
        drawOfferBy = null
        flagJob?.cancel()
        flagGeneration++
    }

    private fun resetClocks() {
        remaining.fill(timeControl.initialSeconds * MILLIS_PER_SECOND)
        tickAt = now()
    }

    private fun remaining(side: Side): Long {
        val stored = remaining[side.ordinal]
        if (!clockRunning || side != sideToMove) {
            return stored
        }
        return (stored - (now() - tickAt)).coerceAtLeast(0)
    }

    private fun touch() {
        lastActivity = now()
    }

    private fun snapshot(): GameSnapshot {
        fun seatOf(side: Side) = seats.firstOrNull { it.side == side }
        return GameSnapshot(
            code = code,
            gameNumber = gameNumber,
            whiteName = seatOf(Side.WHITE)?.name,
            blackName = seatOf(Side.BLACK)?.name,
            timeControl = timeControl,
            moves = moves.toList(),
            whiteMillis = remaining(Side.WHITE),
            blackMillis = remaining(Side.BLACK),
            clockRunning = clockRunning,
            drawOfferBy = drawOfferBy,
            rematchOfferBy = rematchOfferBy,
            result = result,
            whiteConnected = seatOf(Side.WHITE)?.connection != null,
            blackConnected = seatOf(Side.BLACK)?.connection != null,
        )
    }

    private fun joined(seat: Seat) = ServerMessage.Joined(code, seat.token, seat.side, snapshot())

    private suspend fun broadcast(except: Seat? = null) {
        val message = ServerMessage.Snapshot(snapshot())
        seats.filter { it !== except }.forEach { it.send(message) }
    }

    private suspend fun Seat.reject(uci: String, reason: ErrorCode) =
        send(ServerMessage.MoveRejected(uci, reason, snapshot()))

    private suspend fun Seat.fail(code: ErrorCode, message: String) = send(ServerMessage.Error(code, message))

    /** A dead socket must not break the room; the session notices the close on its own. */
    private suspend fun Seat.send(message: ServerMessage) {
        try {
            connection?.send(message)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Delivery failure is handled by the session's disconnect path.
        }
    }
}
