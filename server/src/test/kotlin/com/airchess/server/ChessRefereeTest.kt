package com.airchess.server

import com.airchess.chess.ChessGame
import com.airchess.protocol.GameResult
import com.airchess.protocol.ResultReason
import com.airchess.protocol.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChessRefereeTest {

    private fun refereeAt(fen: String) = ChessReferee(ChessGame(fen))

    @Test
    fun `fool's mate is checkmate for black`() {
        val referee = ChessReferee()
        listOf("f2f3", "e7e5", "g2g4").forEach {
            assertEquals(MoveOutcome.Legal(null), referee.play(it))
        }
        assertEquals(
            MoveOutcome.Legal(GameResult(Side.BLACK, ResultReason.CHECKMATE)),
            referee.play("d8h4"),
        )
        assertEquals(MoveOutcome.Illegal, referee.play("a2a3"))
    }

    @Test
    fun `illegal and malformed moves are refused`() {
        val referee = ChessReferee()
        assertEquals(MoveOutcome.Illegal, referee.play("e2e5"))
        assertEquals(MoveOutcome.Illegal, referee.play("e7e5"))
        assertEquals(MoveOutcome.Illegal, referee.play("zzzz"))
        assertEquals(MoveOutcome.Legal(null), referee.play("E2E4"))
    }

    @Test
    fun `stalemate is a draw`() {
        val referee = refereeAt("7k/5Q2/8/8/8/8/8/K7 w - - 0 1")
        assertEquals(
            MoveOutcome.Legal(GameResult(null, ResultReason.STALEMATE)),
            referee.play("f7g6"),
        )
    }

    @Test
    fun `mating material is detected per side`() {
        val start = ChessReferee()
        assertTrue(start.canMate(Side.WHITE) && start.canMate(Side.BLACK))

        val bishop = refereeAt("7k/8/8/8/8/8/8/KB6 w - - 0 1")
        assertFalse(bishop.canMate(Side.WHITE))
        assertFalse(bishop.canMate(Side.BLACK))

        val rook = refereeAt("7k/8/8/8/8/8/8/KR6 w - - 0 1")
        assertTrue(rook.canMate(Side.WHITE))
        assertFalse(rook.canMate(Side.BLACK))

        val knights = refereeAt("7k/8/8/8/8/8/8/KNN5 w - - 0 1")
        assertTrue(knights.canMate(Side.WHITE))
    }
}
