package com.airchess.server

import com.airchess.protocol.ClientMessage
import com.airchess.protocol.ErrorCode
import com.airchess.protocol.Protocol
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.TimeControl

private const val MAX_NAME_LENGTH = 20
private const val MAX_CLOCK_SECONDS = 24 * 3600
private const val DEFAULT_NAME = "Player"

/** Per-socket state: routes lobby messages itself and hands in-game messages to the room. */
internal class Session(private val registry: RoomRegistry, private val conn: Connection) {
    private var room: GameRoom? = null
    private var seat: Seat? = null

    suspend fun onText(text: String) {
        val message = try {
            Protocol.decodeClient(text)
        } catch (e: IllegalArgumentException) {
            fail(ErrorCode.BAD_REQUEST, "Undecodable message")
            return
        }
        when (message) {
            ClientMessage.Ping -> conn.send(ServerMessage.Pong)
            is ClientMessage.CreateGame -> create(message)
            is ClientMessage.JoinGame -> join(message)
            is ClientMessage.Resume -> resume(message)
            else -> forward(message)
        }
    }

    suspend fun onClose() {
        val room = room ?: return
        val seat = seat ?: return
        room.disconnect(seat, conn)
    }

    private suspend fun create(message: ClientMessage.CreateGame) {
        if (isAttached()) {
            return
        }
        if (!validClock(message.timeControl)) {
            fail(ErrorCode.BAD_REQUEST, "Invalid time control")
            return
        }
        val created = registry.create(message.timeControl, message.color)
        seat = created.join(cleanName(message.name), conn)
        room = created
    }

    private suspend fun join(message: ClientMessage.JoinGame) {
        if (isAttached()) {
            return
        }
        val found = registry.find(message.code)
        if (found == null) {
            fail(ErrorCode.GAME_NOT_FOUND, "No game with code ${message.code}")
            return
        }
        val joined = found.join(cleanName(message.name), conn)
        if (joined == null) {
            fail(ErrorCode.GAME_FULL, "Game is full")
            return
        }
        seat = joined
        room = found
    }

    private suspend fun resume(message: ClientMessage.Resume) {
        if (isAttached()) {
            return
        }
        val found = registry.find(message.code)
        if (found == null) {
            fail(ErrorCode.GAME_NOT_FOUND, "No game with code ${message.code}")
            return
        }
        val resumed = found.resume(message.token, conn)
        if (resumed == null) {
            fail(ErrorCode.INVALID_TOKEN, "Unknown token")
            return
        }
        seat = resumed
        room = found
    }

    private suspend fun forward(message: ClientMessage) {
        val room = room
        val seat = seat
        if (room == null || seat == null) {
            fail(ErrorCode.NOT_IN_GAME, "Join or create a game first")
            return
        }
        room.handle(seat, conn, message)
        if (message == ClientMessage.Leave) {
            this.room = null
            this.seat = null
        }
    }

    /** Reports BAD_REQUEST and returns true when this socket already holds a seat. */
    private suspend fun isAttached(): Boolean {
        if (room == null) {
            return false
        }
        fail(ErrorCode.BAD_REQUEST, "Already in a game")
        return true
    }

    private suspend fun fail(code: ErrorCode, message: String) = conn.send(ServerMessage.Error(code, message))

    private fun cleanName(raw: String) = raw.trim().take(MAX_NAME_LENGTH).ifEmpty { DEFAULT_NAME }

    private fun validClock(control: TimeControl) =
        control.initialSeconds in 1..MAX_CLOCK_SECONDS && control.incrementSeconds in 0..MAX_CLOCK_SECONDS
}
