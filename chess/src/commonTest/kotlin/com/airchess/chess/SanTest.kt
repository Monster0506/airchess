package com.airchess.chess

import kotlin.test.assertEquals
import kotlin.test.Test

class SanTest {

    @Test
    fun sampleGame() {
        val game = ChessGame()
        game.play(
            "e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6",
            "f3g5", "d7d5", "e4d5", "c6a5",
        )
        val expected = listOf("e4", "e5", "Nf3", "Nc6", "Bc4", "Nf6", "Ng5", "d5", "exd5", "Na5")
        assertEquals(expected, game.moveHistory.map { it.san })
    }

    @Test
    fun knightDisambiguationByFile() {
        val game = ChessGame("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1")
        game.play("b1d2")
        assertEquals("Nbd2", game.moveHistory.last().san)

        val other = ChessGame("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1")
        other.play("f3d2")
        assertEquals("Nfd2", other.moveHistory.last().san)
    }

    @Test
    fun rookDisambiguationByRank() {
        val game = ChessGame("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1")
        game.play("a1a3")
        assertEquals("R1a3", game.moveHistory.last().san)

        val other = ChessGame("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1")
        other.play("a5a3")
        assertEquals("R5a3", other.moveHistory.last().san)
    }

    @Test
    fun fullSquareDisambiguation() {
        val game = ChessGame("4k3/8/8/8/8/Q7/8/Q1Q1K3 w - - 0 1")
        game.play("a1c3")
        assertEquals("Qa1c3", game.moveHistory.last().san)
    }

    @Test
    fun pinnedPieceDoesNotNeedDisambiguation() {
        val game = ChessGame("4k3/8/2b5/8/8/5N2/8/1N5K w - - 0 1")
        game.play("b1d2")
        assertEquals("Nd2", game.moveHistory.last().san)
    }

    @Test
    fun pawnCaptureAndPromotion() {
        val game = ChessGame("1n6/P6k/8/8/8/8/8/K7 w - - 0 1")
        game.play("a7b8q")
        assertEquals("axb8=Q", game.moveHistory.last().san)

        val quiet = ChessGame("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        quiet.play("a7a8n")
        assertEquals("a8=N", quiet.moveHistory.last().san)
    }

    @Test
    fun promotionWithCheckAndMate() {
        val game = ChessGame("7k/P7/8/8/8/8/8/K7 w - - 0 1")
        game.play("a7a8q")
        assertEquals("a8=Q+", game.moveHistory.last().san)

        val mate = ChessGame("7k/P7/6K1/8/8/8/8/8 w - - 0 1")
        mate.play("a7a8q")
        assertEquals("a8=Q#", mate.moveHistory.last().san)
    }

    @Test
    fun captureWithPieceAndCheck() {
        val game = ChessGame("4k3/8/8/8/8/8/4p3/4K1R1 w - - 0 1")
        game.play("g1g8")
        assertEquals("Rg8+", game.moveHistory.last().san)

        val capture = ChessGame("4k3/8/8/8/8/8/4p3/4KR2 w - - 0 1")
        capture.play("e1e2")
        assertEquals("Kxe2", capture.moveHistory.last().san)
    }
}
