package com.airchess.server

import com.airchess.protocol.CameraSignalKind
import com.airchess.protocol.ClientMessage
import com.airchess.protocol.ColorChoice
import com.airchess.protocol.ErrorCode
import com.airchess.protocol.GameResult
import com.airchess.protocol.GameSnapshot
import com.airchess.protocol.Protocol
import com.airchess.protocol.ResultReason
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.Side
import com.airchess.protocol.TimeControl
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.withTimeout

private const val RECEIVE_TIMEOUT_MS = 5_000L

private class Player(private val ws: DefaultClientWebSocketSession) {
    suspend fun send(message: ClientMessage) = ws.send(Frame.Text(Protocol.encode(message)))

    suspend fun sendRaw(text: String) = ws.send(Frame.Text(text))

    suspend fun nextRaw(): ServerMessage = withTimeout(RECEIVE_TIMEOUT_MS) {
        Protocol.decodeServer((ws.incoming.receive() as Frame.Text).readText())
    }

    suspend fun next(): ServerMessage {
        while (true) {
            val message = nextRaw()
            if (message !is ServerMessage.CameraConfig) {
                return message
            }
        }
    }

    suspend fun until(accept: (ServerMessage) -> Boolean): ServerMessage {
        while (true) {
            val message = next()
            if (accept(message)) {
                return message
            }
        }
    }

    suspend fun snapshotWhere(accept: (GameSnapshot) -> Boolean): GameSnapshot =
        until { snapshotOf(it)?.let(accept) == true }.let { snapshotOf(it)!! }

    suspend fun close() = ws.close()
}

private fun snapshotOf(message: ServerMessage): GameSnapshot? = when (message) {
    is ServerMessage.Joined -> message.snapshot
    is ServerMessage.Snapshot -> message.snapshot
    is ServerMessage.MoveRejected -> message.snapshot
    else -> null
}

class ServerIntegrationTest {

    private fun serverTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module() }
        block()
    }

    private suspend fun ApplicationTestBuilder.connect(): Player {
        val client = createClient { install(WebSockets) }
        return Player(client.webSocketSession(Protocol.WEBSOCKET_PATH))
    }

    private suspend fun ApplicationTestBuilder.seatedPair(): Triple<Player, Player, ServerMessage.Joined> {
        val white = connect()
        white.send(ClientMessage.CreateGame("Ann", TimeControl.BLITZ_5_0, ColorChoice.WHITE))
        val created = assertIs<ServerMessage.Joined>(white.next())
        val black = connect()
        black.send(ClientMessage.JoinGame(created.code, "Bob"))
        assertIs<ServerMessage.Joined>(black.next())
        white.snapshotWhere { it.started }
        return Triple(white, black, created)
    }

    @Test
    fun `root answers with room count`() = serverTest {
        val text = client.get("/").bodyAsText()
        assertTrue(text.startsWith("AirChess server OK"), text)
    }

    @Test
    fun `camera signals stay within the authenticated room`() = serverTest {
        val (white, black, _) = seatedPair()
        val outsider = connect()
        outsider.send(ClientMessage.CameraSignal(CameraSignalKind.READY))
        assertEquals(ErrorCode.NOT_IN_GAME, assertIs<ServerMessage.Error>(outsider.next()).code)
        outsider.send(ClientMessage.CreateGame("Other room", TimeControl.BLITZ_5_0, ColorChoice.WHITE))
        assertIs<ServerMessage.Joined>(outsider.next())
        outsider.send(ClientMessage.CameraSignal(CameraSignalKind.READY))

        white.send(ClientMessage.CameraSignal(CameraSignalKind.READY))
        assertIs<ServerMessage.CameraSignal>(black.next())
        black.send(ClientMessage.CameraSignal(CameraSignalKind.READY))
        assertIs<ServerMessage.CameraSignal>(white.next())
        assertIs<ServerMessage.CameraSignal>(black.next())
        white.send(ClientMessage.CameraSignal(CameraSignalKind.OFFER, "sdp"))
        assertEquals("sdp", assertIs<ServerMessage.CameraSignal>(black.next()).payload)
        outsider.send(ClientMessage.Ping)
        assertEquals(ServerMessage.Pong, outsider.next())
    }

    @Test
    fun `fool's mate ends in checkmate for black`() = serverTest {
        val (white, black, created) = seatedPair()
        assertEquals(Side.WHITE, created.side)

        val moves = listOf("f2f3", "e7e5", "g2g4", "d8h4")
        moves.dropLast(1).forEachIndexed { ply, uci ->
            val mover = if (ply % 2 == 0) white else black
            mover.send(ClientMessage.MakeMove(uci, ply))
            white.snapshotWhere { it.moves.size == ply + 1 }
            black.snapshotWhere { it.moves.size == ply + 1 }
        }
        black.send(ClientMessage.MakeMove(moves.last(), moves.size - 1))

        val expected = GameResult(Side.BLACK, ResultReason.CHECKMATE)
        assertEquals(expected, white.snapshotWhere { it.moves.size == moves.size }.result)
        assertEquals(expected, black.snapshotWhere { it.moves.size == moves.size }.result)
    }

    @Test
    fun `illegal move is rejected with ILLEGAL_MOVE`() = serverTest {
        val (white, _, _) = seatedPair()
        white.send(ClientMessage.MakeMove("e2e5", 0))
        val rejected = assertIs<ServerMessage.MoveRejected>(white.until { it is ServerMessage.MoveRejected })
        assertEquals(ErrorCode.ILLEGAL_MOVE, rejected.reason)
        assertTrue(rejected.snapshot.moves.isEmpty())

        white.send(ClientMessage.MakeMove("e2e4", 0))
        white.snapshotWhere { it.moves == listOf("e2e4") }
    }

    @Test
    fun `lobby errors keep the session alive`() = serverTest {
        val player = connect()
        player.send(ClientMessage.MakeMove("e2e4", 0))
        assertEquals(ErrorCode.NOT_IN_GAME, assertIs<ServerMessage.Error>(player.next()).code)

        player.send(ClientMessage.JoinGame("ZZZZZZ", "Bob"))
        assertEquals(ErrorCode.GAME_NOT_FOUND, assertIs<ServerMessage.Error>(player.next()).code)

        player.send(ClientMessage.Resume("ZZZZZZ", "token"))
        assertEquals(ErrorCode.GAME_NOT_FOUND, assertIs<ServerMessage.Error>(player.next()).code)

        player.sendRaw("{not json")
        assertEquals(ErrorCode.BAD_REQUEST, assertIs<ServerMessage.Error>(player.next()).code)

        player.send(ClientMessage.Ping)
        assertEquals(ServerMessage.Pong, player.next())
    }

    @Test
    fun `third player gets GAME_FULL and bad token is refused`() = serverTest {
        val (_, _, created) = seatedPair()
        val extra = connect()
        extra.send(ClientMessage.JoinGame(created.code.lowercase(), "Cy"))
        assertEquals(ErrorCode.GAME_FULL, assertIs<ServerMessage.Error>(extra.next()).code)

        extra.send(ClientMessage.Resume(created.code, "wrong"))
        assertEquals(ErrorCode.INVALID_TOKEN, assertIs<ServerMessage.Error>(extra.next()).code)
    }

    @Test
    fun `dropped player resumes the same seat`() = serverTest {
        val (white, black, created) = seatedPair()
        white.send(ClientMessage.MakeMove("e2e4", 0))
        black.snapshotWhere { it.moves.size == 1 }

        white.close()
        black.snapshotWhere { !it.whiteConnected }

        val again = connect()
        again.send(ClientMessage.Resume(created.code, created.token))
        val joined = assertIs<ServerMessage.Joined>(again.next())
        assertEquals(Side.WHITE, joined.side)
        assertEquals(listOf("e2e4"), joined.snapshot.moves)
        black.snapshotWhere { it.whiteConnected }
    }
}
