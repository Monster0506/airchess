package com.airchess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FenTest {

    private val samples = listOf(
        Fen.START,
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        "4k3/8/8/8/8/8/8/4K3 b - - 37 90",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
    )

    @Test
    fun startParsesToInitialPosition() {
        assertEquals(ChessPosition.INITIAL, Fen.parse(Fen.START))
    }

    @Test
    fun roundTrip() {
        for (fen in samples) {
            assertEquals(fen, Fen.export(Fen.parse(fen)))
        }
    }

    @Test
    fun parsesFields() {
        val position = Fen.parse("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b Kq e3 5 7")
        assertEquals(ChessColor.BLACK, position.sideToMove)
        assertEquals(CastlingRights(true, false, false, true), position.castling)
        assertEquals(sq("e3"), position.enPassant)
        assertEquals(5, position.halfmoveClock)
        assertEquals(7, position.fullmoveNumber)
        assertEquals(ChessPiece(ChessColor.WHITE, PieceType.PAWN), position[sq("e4")])
        assertNull(position[sq("e2")])
    }

    @Test
    fun missingCountersDefault() {
        val position = Fen.parse("4k3/8/8/8/8/8/8/4K3 w - -")
        assertEquals(0, position.halfmoveClock)
        assertEquals(1, position.fullmoveNumber)
    }

    @Test
    fun malformedFensThrow() {
        val bad = listOf(
            "",
            "garbage",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR/8 w KQkq - 0 1",
            "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/7/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnx/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQxq - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e9 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e4 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - -1 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - a 1",
            "8/8/8/8/8/8/8/4K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/4K2K w - - 0 1",
            "P3k3/8/8/8/8/8/8/4K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/p3K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/R3K3 w - - 0 1 extra",
            "4k3/8/8/8/8/8/4R3/4K3 w - - 0 1",
        )
        for (fen in bad) {
            assertThrows("Expected failure for '$fen'", IllegalArgumentException::class.java) {
                Fen.parse(fen)
            }
        }
    }
}
