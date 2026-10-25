package com.airchess.protocol

import kotlin.test.assertEquals
import kotlin.test.Test

class ProtocolTest {

    private val snapshot = GameSnapshot(
        code = "ABC234",
        gameNumber = 1,
        whiteName = "Ann",
        blackName = null,
        timeControl = TimeControl.BLITZ_5_0,
        moves = listOf("e2e4"),
        whiteMillis = 300_000,
        blackMillis = 299_000,
        clockRunning = true,
        drawOfferBy = Side.BLACK,
    )

    @Test
    fun clientMessagesRoundTrip() {
        val messages = listOf(
            ClientMessage.CreateGame("Ann", TimeControl.BLITZ_3_2, ColorChoice.RANDOM),
            ClientMessage.JoinGame("ABC234", "Bob"),
            ClientMessage.Resume("ABC234", "tok"),
            ClientMessage.MakeMove("e7e8q", 41),
            ClientMessage.Resign,
            ClientMessage.RespondDraw(accept = true),
            ClientMessage.Ping,
        )

        messages.forEach { assertEquals(it, Protocol.decodeClient(Protocol.encode(it))) }
    }

    @Test
    fun serverMessagesRoundTrip() {
        val messages = listOf(
            ServerMessage.Joined("ABC234", "tok", Side.WHITE, snapshot),
            ServerMessage.Snapshot(snapshot.copy(result = GameResult(null, ResultReason.DRAW_AGREED))),
            ServerMessage.MoveRejected("e2e5", ErrorCode.ILLEGAL_MOVE, snapshot),
            ServerMessage.Error(ErrorCode.GAME_NOT_FOUND, "nope"),
            ServerMessage.Pong,
        )

        messages.forEach { assertEquals(it, Protocol.decodeServer(Protocol.encode(it))) }
    }

    @Test
    fun usesTypeDiscriminator() {
        val text = Protocol.encode(ClientMessage.MakeMove("e2e4", 0))

        assertEquals("""{"type":"move","uci":"e2e4","ply":0}""", text)
    }

    @Test
    fun sideToMoveFollowsMoveCount() {
        assertEquals(Side.BLACK, snapshot.sideToMove)
        assertEquals(Side.WHITE, snapshot.copy(moves = emptyList()).sideToMove)
    }
}
