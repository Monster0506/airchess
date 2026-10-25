package com.airchess.server

import com.airchess.protocol.CameraSignalKind
import com.airchess.protocol.ClientMessage
import com.airchess.protocol.ErrorCode
import com.airchess.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CameraSignalTest {
    @Test
    fun `camera negotiation requires both players to opt in`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(signal(CameraSignalKind.OFFER, "offer"))
        assertEquals(ErrorCode.BAD_REQUEST, table.annConn.lastError())
        assertTrue(table.bobConn.all<ServerMessage.CameraSignal>().isEmpty())

        table.ann(signal(CameraSignalKind.READY))
        assertEquals(CameraSignalKind.READY, table.bobConn.last<ServerMessage.CameraSignal>().kind)
        assertTrue(table.annConn.all<ServerMessage.CameraSignal>().isEmpty())
        table.ann(signal(CameraSignalKind.ICE, "candidate"))
        assertEquals(ErrorCode.BAD_REQUEST, table.annConn.lastError())

        table.bob(signal(CameraSignalKind.READY))
        assertEquals(CameraSignalKind.READY, table.annConn.last<ServerMessage.CameraSignal>().kind)
        assertEquals(CameraSignalKind.READY, table.bobConn.last<ServerMessage.CameraSignal>().kind)
        table.ann(signal(CameraSignalKind.OFFER, "offer"))
        assertEquals(ServerMessage.CameraSignal(CameraSignalKind.OFFER, "offer"), table.bobConn.last<ServerMessage.CameraSignal>())
        table.bob(signal(CameraSignalKind.ANSWER, "answer"))
        assertEquals(ServerMessage.CameraSignal(CameraSignalKind.ANSWER, "answer"), table.annConn.last<ServerMessage.CameraSignal>())
        table.ann(ClientMessage.MakeMove("e2e4", 0))
        assertEquals(listOf("e2e4"), table.bobConn.snapshot().moves)
    }

    @Test
    fun `stopping and disconnecting revoke camera consent`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(signal(CameraSignalKind.READY))
        table.bob(signal(CameraSignalKind.READY))
        table.ann(signal(CameraSignalKind.STOP))
        assertEquals(CameraSignalKind.STOP, table.bobConn.last<ServerMessage.CameraSignal>().kind)
        table.bob(signal(CameraSignalKind.OFFER, "offer"))
        assertEquals(ErrorCode.BAD_REQUEST, table.bobConn.lastError())

        table.ann(signal(CameraSignalKind.READY))
        table.room.disconnect(table.annSeat, table.annConn)
        assertEquals(CameraSignalKind.STOP, table.bobConn.last<ServerMessage.CameraSignal>().kind)
        table.bob(signal(CameraSignalKind.ICE, "candidate"))
        assertEquals(ErrorCode.BAD_REQUEST, table.bobConn.lastError())
    }

    @Test
    fun `resuming invalidates the old camera connection`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(signal(CameraSignalKind.READY))
        table.bob(signal(CameraSignalKind.READY))
        val resumed = FakeConnection()
        table.room.resume(table.annSeat.token, resumed)
        assertEquals(CameraSignalKind.STOP, table.bobConn.last<ServerMessage.CameraSignal>().kind)
        val count = table.bobConn.all<ServerMessage.CameraSignal>().size
        table.ann(signal(CameraSignalKind.OFFER, "stale"))
        assertEquals(count, table.bobConn.all<ServerMessage.CameraSignal>().size)
        table.room.handle(table.annSeat, resumed, signal(CameraSignalKind.OFFER, "no consent"))
        assertEquals(ErrorCode.BAD_REQUEST, resumed.lastError())
    }

    @Test
    fun `oversized signaling is rejected without reaching opponent`() = runTest {
        val table = Table(this).seatBoth()
        table.ann(signal(CameraSignalKind.READY))
        table.bob(signal(CameraSignalKind.READY))
        val count = table.bobConn.all<ServerMessage.CameraSignal>().size
        table.ann(signal(CameraSignalKind.OFFER, "x".repeat(49 * 1024)))
        assertEquals(ErrorCode.BAD_REQUEST, table.annConn.lastError())
        assertEquals(count, table.bobConn.all<ServerMessage.CameraSignal>().size)
    }

    private fun signal(kind: CameraSignalKind, payload: String = "") = ClientMessage.CameraSignal(kind, payload)
}
