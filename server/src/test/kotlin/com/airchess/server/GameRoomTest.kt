package com.airchess.server

import com.airchess.protocol.ClientMessage.Leave
import com.airchess.protocol.ClientMessage.MakeMove
import com.airchess.protocol.ClientMessage.OfferDraw
import com.airchess.protocol.ClientMessage.OfferRematch
import com.airchess.protocol.ClientMessage.Resign
import com.airchess.protocol.ClientMessage.RespondDraw
import com.airchess.protocol.ClientMessage.RespondRematch
import com.airchess.protocol.ErrorCode
import com.airchess.protocol.GameResult
import com.airchess.protocol.ResultReason
import com.airchess.protocol.ServerMessage
import com.airchess.protocol.Side
import com.airchess.protocol.TimeControl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GameRoomTest {

    @Test
    fun `creator gets chosen side and joiner the opposite`() = runTest {
        val table = Table(this, creatorColor = Side.WHITE)
        val ann = table.room.join("Ann", table.annConn)!!
        assertEquals(Side.WHITE, table.annConn.last<ServerMessage.Joined>().side)
        assertNull(table.annConn.snapshot().blackName)

        val bob = table.room.join("Bob", table.bobConn)!!
        assertEquals(Side.BLACK, table.bobConn.last<ServerMessage.Joined>().side)
        assertEquals(Side.BLACK, bob.side)
        assertEquals(Side.WHITE, ann.side)

        val snapshot = table.annConn.last<ServerMessage.Snapshot>().snapshot
        assertEquals("Ann", snapshot.whiteName)
        assertEquals("Bob", snapshot.blackName)
        assertTrue(snapshot.whiteConnected && snapshot.blackConnected)
        assertNotEquals(ann.token, bob.token)
    }

    @Test
    fun `explicit black choice seats creator as black`() = runTest {
        val table = Table(this, creatorColor = Side.BLACK).seatBoth()
        assertEquals(Side.BLACK, table.annSeat.side)
        assertEquals(Side.WHITE, table.bobSeat.side)
        val snapshot = table.bobConn.snapshot()
        assertEquals("Bob", snapshot.whiteName)
        assertEquals("Ann", snapshot.blackName)
    }

    @Test
    fun `third player is refused`() = runTest {
        val table = Table(this).seatBoth()
        assertNull(table.room.join("Cy", FakeConnection()))
    }

    @Test
    fun `moves alternate and are recorded`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.bob(MakeMove("e7e5", 1))
        assertEquals(listOf("e2e4", "e7e5"), table.annConn.snapshot().moves)
        assertEquals(listOf("e2e4", "e7e5"), table.bobConn.snapshot().moves)
    }

    @Test
    fun `black cannot open`() = runTest {
        val table = Table(this).seatBoth()
        table.bob(MakeMove("e7e5", 0))
        val rejected = table.bobConn.last<ServerMessage.MoveRejected>()
        assertEquals(ErrorCode.NOT_YOUR_TURN, rejected.reason)
        assertTrue(rejected.snapshot.moves.isEmpty())
    }

    @Test
    fun `wrong ply is rejected`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(MakeMove("e2e4", 1))
        assertEquals(ErrorCode.BAD_REQUEST, table.annConn.last<ServerMessage.MoveRejected>().reason)
        table.ann(MakeMove("e2e4", 0))
        table.ann(MakeMove("d2d4", 0))
        assertEquals(listOf("e2e4"), table.bobConn.snapshot().moves)
    }

    @Test
    fun `illegal move is rejected and not recorded`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(MakeMove("e2e5", 0))
        assertEquals(ErrorCode.ILLEGAL_MOVE, table.annConn.last<ServerMessage.MoveRejected>().reason)
        assertTrue(table.bobConn.snapshot().moves.isEmpty())
    }

    @Test
    fun `move before opponent joins is rejected`() = runTest {
        val table = Table(this)
        val ann = table.room.join("Ann", table.annConn)!!
        table.room.handle(ann, table.annConn, MakeMove("e2e4", 0))
        assertEquals(ErrorCode.BAD_REQUEST, table.annConn.last<ServerMessage.MoveRejected>().reason)
    }

    @Test
    fun `first move of each side is untimed`() = runTest {
        val table = Table(this).seatBoth()
        pass(10_000)
        table.ann(MakeMove("e2e4", 0))
        assertFalse(table.bobConn.snapshot().clockRunning)
        pass(10_000)
        table.bob(MakeMove("e7e5", 1))

        val snapshot = table.annConn.snapshot()
        assertTrue(snapshot.clockRunning)
        assertEquals(60_000, snapshot.whiteMillis)
        assertEquals(60_000, snapshot.blackMillis)
    }

    @Test
    fun `mover is charged elapsed time then gets increment`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.bob(MakeMove("e7e5", 1))

        pass(5_000)
        table.ann(MakeMove("g1f3", 2))
        assertEquals(57_000, table.bobConn.snapshot().whiteMillis)

        pass(3_000)
        table.bob(MakeMove("b8c6", 3))
        val snapshot = table.annConn.snapshot()
        assertEquals(57_000, snapshot.whiteMillis)
        assertEquals(59_000, snapshot.blackMillis)
    }

    @Test
    fun `running clock shows remaining time at send time`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.bob(MakeMove("e7e5", 1))
        pass(4_000)
        table.ann(OfferDraw)
        val snapshot = table.bobConn.snapshot()
        assertEquals(56_000, snapshot.whiteMillis)
        assertEquals(60_000, snapshot.blackMillis)
    }

    @Test
    fun `flag fall loses on time`() = runTest {
        val table = Table(this, timeControl = TimeControl(10, 0)).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.bob(MakeMove("e7e5", 1))

        pass(9_999)
        assertNull(table.annConn.snapshot().result)
        pass(1)

        val snapshot = table.annConn.snapshot()
        assertEquals(GameResult(Side.BLACK, ResultReason.TIMEOUT), snapshot.result)
        assertEquals(0, snapshot.whiteMillis)
        assertFalse(snapshot.clockRunning)
        assertEquals(snapshot, table.bobConn.snapshot())
    }

    @Test
    fun `flag fall against bare king is a draw`() = runTest {
        val table = Table(
            this,
            timeControl = TimeControl(10, 0),
            makeReferee = { ScriptedReferee(mating = false) },
        ).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.bob(MakeMove("e7e5", 1))
        pass(10_000)
        assertEquals(
            GameResult(null, ResultReason.INSUFFICIENT_MATERIAL),
            table.annConn.snapshot().result,
        )
    }

    @Test
    fun `answering moves keep the flag from falling`() = runTest {
        val table = Table(this, timeControl = TimeControl(10, 0)).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.bob(MakeMove("e7e5", 1))
        pass(9_000)
        table.ann(MakeMove("g1f3", 2))
        pass(9_000)
        table.bob(MakeMove("b8c6", 3))
        pass(500)
        assertNull(table.annConn.snapshot().result)
    }

    @Test
    fun `referee result ends the game and rejects further moves`() = runTest {
        val mate = GameResult(Side.WHITE, ResultReason.CHECKMATE)
        val table = Table(this, makeReferee = { ScriptedReferee(results = mapOf("d1h5" to mate)) }).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.bob(MakeMove("e7e5", 1))
        table.ann(MakeMove("d1h5", 2))
        val snapshot = table.bobConn.snapshot()
        assertEquals(mate, snapshot.result)
        assertFalse(snapshot.clockRunning)

        table.bob(MakeMove("a7a6", 3))
        assertEquals(ErrorCode.GAME_OVER, table.bobConn.last<ServerMessage.MoveRejected>().reason)
    }

    @Test
    fun `resign gives opponent the win`() = runTest {
        val table = Table(this).seatBoth()
        table.bob(Resign)
        assertEquals(GameResult(Side.WHITE, ResultReason.RESIGNATION), table.annConn.snapshot().result)
        assertEquals(GameResult(Side.WHITE, ResultReason.RESIGNATION), table.bobConn.snapshot().result)
    }

    @Test
    fun `draw offer accepted ends the game`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(OfferDraw)
        assertEquals(Side.WHITE, table.bobConn.snapshot().drawOfferBy)
        table.bob(RespondDraw(true))
        val snapshot = table.annConn.snapshot()
        assertEquals(GameResult(null, ResultReason.DRAW_AGREED), snapshot.result)
        assertNull(snapshot.drawOfferBy)
    }

    @Test
    fun `draw offer declined clears and can be repeated`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(OfferDraw)
        table.bob(RespondDraw(false))
        assertNull(table.annConn.snapshot().drawOfferBy)
        assertNull(table.annConn.snapshot().result)
        table.ann(OfferDraw)
        assertEquals(Side.WHITE, table.annConn.snapshot().drawOfferBy)
    }

    @Test
    fun `draw offer is cleared by a move`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.bob(OfferDraw)
        assertEquals(Side.BLACK, table.annConn.snapshot().drawOfferBy)
        table.bob(MakeMove("e7e5", 1))
        assertNull(table.annConn.snapshot().drawOfferBy)
    }

    @Test
    fun `draw offer cannot be repeated or answered by the offerer`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(OfferDraw)
        table.ann(OfferDraw)
        assertEquals(ErrorCode.BAD_REQUEST, table.annConn.lastError())
        table.ann(RespondDraw(true))
        assertNull(table.annConn.snapshot().result)
        table.bob(RespondDraw(false))
        table.bob(RespondDraw(true))
        assertEquals(ErrorCode.BAD_REQUEST, table.bobConn.lastError())
    }

    @Test
    fun `rematch swaps colors and resets the game`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.bob(MakeMove("e7e5", 1))
        table.bob(Resign)
        table.bob(OfferRematch)
        assertEquals(Side.BLACK, table.annConn.snapshot().rematchOfferBy)
        table.ann(RespondRematch(true))

        val annJoined = table.annConn.last<ServerMessage.Joined>()
        val bobJoined = table.bobConn.last<ServerMessage.Joined>()
        assertEquals(Side.BLACK, annJoined.side)
        assertEquals(Side.WHITE, bobJoined.side)
        assertEquals("token-1", annJoined.token)

        val snapshot = bobJoined.snapshot
        assertEquals(2, snapshot.gameNumber)
        assertTrue(snapshot.moves.isEmpty())
        assertNull(snapshot.result)
        assertNull(snapshot.rematchOfferBy)
        assertEquals("Bob", snapshot.whiteName)
        assertEquals(60_000, snapshot.whiteMillis)
        assertEquals(2, table.referees.size)

        table.bob(MakeMove("d2d4", 0))
        assertEquals(listOf("d2d4"), table.annConn.snapshot().moves)
    }

    @Test
    fun `rematch needs a finished game and can be declined`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(OfferRematch)
        assertEquals(ErrorCode.BAD_REQUEST, table.annConn.lastError())

        table.ann(Resign)
        table.ann(OfferRematch)
        table.bob(RespondRematch(false))
        val snapshot = table.annConn.snapshot()
        assertNull(snapshot.rematchOfferBy)
        assertEquals(1, snapshot.gameNumber)
    }

    @Test
    fun `disconnect and resume keeps the seat`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(MakeMove("e2e4", 0))
        table.room.disconnect(table.annSeat, table.annConn)
        assertFalse(table.bobConn.snapshot().whiteConnected)

        val again = FakeConnection()
        val seat = table.room.resume("token-1", again)
        assertTrue(seat === table.annSeat)
        assertNotNull(seat)
        val joined = again.last<ServerMessage.Joined>()
        assertEquals(Side.WHITE, joined.side)
        assertEquals(listOf("e2e4"), joined.snapshot.moves)
        assertTrue(table.bobConn.snapshot().whiteConnected)

        table.bob(MakeMove("e7e5", 1))
        assertEquals(listOf("e2e4", "e7e5"), again.snapshot().moves)
    }

    @Test
    fun `resume with a bad token fails`() = runTest {
        val table = Table(this).seatBoth()
        assertNull(table.room.resume("nope", FakeConnection()))
    }

    @Test
    fun `replaced connection is ignored`() = runTest {
        val table = Table(this).seatBoth()
        table.room.resume("token-1", FakeConnection())
        table.ann(MakeMove("e2e4", 0))
        assertTrue(table.bobConn.snapshot().moves.isEmpty())
        table.room.disconnect(table.annSeat, table.annConn)
        assertTrue(table.bobConn.snapshot().whiteConnected)
    }

    @Test
    fun `player is abandoned after the grace period`() = runTest {
        val table = Table(this).seatBoth()
        table.room.disconnect(table.bobSeat, table.bobConn)
        pass(59_999)
        assertNull(table.annConn.snapshot().result)
        pass(1)
        assertEquals(GameResult(Side.WHITE, ResultReason.ABANDONED), table.annConn.snapshot().result)
    }

    @Test
    fun `resume within the grace period cancels abandonment`() = runTest {
        val table = Table(this).seatBoth()
        table.room.disconnect(table.bobSeat, table.bobConn)
        pass(30_000)
        assertNotNull(table.room.resume("token-2", FakeConnection()))
        pass(60_000)
        assertNull(table.annConn.snapshot().result)
    }

    @Test
    fun `creator who left before opponent joined is abandoned after join`() = runTest {
        val table = Table(this)
        val ann = table.room.join("Ann", table.annConn)!!
        table.room.disconnect(ann, table.annConn)
        table.room.join("Bob", table.bobConn)
        pass(60_000)
        assertEquals(GameResult(Side.BLACK, ResultReason.ABANDONED), table.bobConn.snapshot().result)
    }

    @Test
    fun `leave abandons immediately`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(Leave)
        val snapshot = table.bobConn.snapshot()
        assertEquals(GameResult(Side.BLACK, ResultReason.ABANDONED), snapshot.result)
        assertFalse(snapshot.whiteConnected)
    }

    @Test
    fun `room is idle only after everyone left and the ttl passed`() = runTest {
        val table = Table(this).seatBoth()
        pass(11 * 60_000)
        assertFalse(table.room.isIdle(10 * 60_000))
        table.room.disconnect(table.annSeat, table.annConn)
        table.room.disconnect(table.bobSeat, table.bobConn)
        assertFalse(table.room.isIdle(10 * 60_000))
        pass(10 * 60_000 + 1)
        assertTrue(table.room.isIdle(10 * 60_000))
    }
}
