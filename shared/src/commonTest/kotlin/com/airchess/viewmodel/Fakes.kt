package com.airchess.viewmodel

import com.airchess.chess.ChessSquare
import com.airchess.data.Prefs
import com.airchess.model.GameSettings
import com.airchess.engine.ChessEngine
import com.airchess.engine.EngineAnalysis
import com.airchess.engine.EngineScore
import com.airchess.net.ConnectionState
import com.airchess.net.OnlineGameClient
import com.airchess.protocol.ClientMessage
import com.airchess.protocol.GameSnapshot
import com.airchess.protocol.GameResult
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.Side
import com.airchess.protocol.TimeControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.IOException

fun sq(name: String): ChessSquare = ChessSquare.fromAlgebraic(name)

class FakeEngine(
    var bestMove: String? = "e2e4",
    var score: EngineScore? = EngineScore.Centipawns(35),
    var failure: Exception? = null,
) : ChessEngine {
    val fens = mutableListOf<String>()
    val depths = mutableListOf<Int>()
    var closed = false

    override suspend fun analyze(fen: String, depth: Int): EngineAnalysis {
        fens += fen
        depths += depth
        failure?.let { throw it }
        return EngineAnalysis(bestMove, score, listOfNotNull(bestMove), depth)
    }

    override fun close() {
        closed = true
    }
}

class FakePrefs : Prefs {
    override var playerName = "Player"
    override var serverUrl = "ws://localhost:8080/ws"
    override var settings = GameSettings()
    override var onboardingComplete = true
    override var prefer3d = false
    override var savedMoves = ""
    override var savedMode = ""
    override var savedComputerSide = "WHITE"
    override var savedComputerDifficulty = "MEDIUM"
    override var savedOnlineCode = ""
    override var savedOnlineToken = ""
}

class FakeClient : OnlineGameClient {
    private val inbox = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    private val connection = MutableStateFlow(ConnectionState.DISCONNECTED)

    val sent = mutableListOf<ClientMessage>()
    var connectCount = 0
    var failConnects = 0
    var closed = false

    override val incoming: Flow<ServerMessage> = inbox

    override val state: StateFlow<ConnectionState> = connection

    override suspend fun connect(url: String) {
        connectCount++
        if (failConnects > 0) {
            failConnects--
            throw IOException("unreachable")
        }
        connection.value = ConnectionState.CONNECTED
    }

    override fun send(message: ClientMessage): Boolean {
        if (connection.value != ConnectionState.CONNECTED) {
            return false
        }
        sent += message
        return true
    }

    override fun close() {
        closed = true
        connection.value = ConnectionState.DISCONNECTED
    }

    suspend fun deliver(message: ServerMessage) {
        inbox.emit(message)
    }

    fun drop() {
        connection.value = ConnectionState.DISCONNECTED
    }
}

fun snapshot(
    moves: List<String> = emptyList(),
    gameNumber: Int = 1,
    whiteName: String? = "Alice",
    blackName: String? = "Bob",
    whiteMillis: Long = 60_000,
    blackMillis: Long = 60_000,
    clockRunning: Boolean = false,
    drawOfferBy: Side? = null,
    rematchOfferBy: Side? = null,
    result: GameResult? = null,
) = GameSnapshot(
    code = "ABC234",
    gameNumber = gameNumber,
    whiteName = whiteName,
    blackName = blackName,
    timeControl = TimeControl.BULLET_1_0,
    moves = moves,
    whiteMillis = whiteMillis,
    blackMillis = blackMillis,
    clockRunning = clockRunning,
    drawOfferBy = drawOfferBy,
    rematchOfferBy = rematchOfferBy,
    result = result,
    whiteConnected = true,
    blackConnected = true,
)

fun joined(side: Side, snapshot: GameSnapshot = snapshot()) =
    ServerMessage.Joined(code = snapshot.code, token = "token-1", side = side, snapshot = snapshot)
