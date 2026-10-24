package com.airchess.viewmodel

import com.airchess.chess.ChessColor
import com.airchess.chess.GameStatus
import com.airchess.engine.EngineScore
import com.airchess.protocol.GameResult
import com.airchess.protocol.ResultReason
import com.airchess.protocol.Side
import com.airchess.ui.formatClock
import com.airchess.ui.sanitizeCode
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun centipawnsAreShownFromWhitePerspective() {
        assertEquals("+0.35", formatEval(EngineScore.Centipawns(35), ChessColor.WHITE))
        assertEquals("-0.35", formatEval(EngineScore.Centipawns(35), ChessColor.BLACK))
        assertEquals("-1.20", formatEval(EngineScore.Centipawns(-120), ChessColor.WHITE))
        assertEquals("+1.05", formatEval(EngineScore.Centipawns(-105), ChessColor.BLACK))
        assertEquals("0.00", formatEval(EngineScore.Centipawns(0), ChessColor.BLACK))
    }

    @Test
    fun matesAreShownFromWhitePerspective() {
        assertEquals("M3", formatEval(EngineScore.Mate(3), ChessColor.WHITE))
        assertEquals("-M2", formatEval(EngineScore.Mate(-2), ChessColor.WHITE))
        assertEquals("-M3", formatEval(EngineScore.Mate(3), ChessColor.BLACK))
        assertEquals("M2", formatEval(EngineScore.Mate(-2), ChessColor.BLACK))
    }

    @Test
    fun localStatusTexts() {
        assertEquals("White to move", localStatusText(GameStatus.IN_PROGRESS, ChessColor.WHITE))
        assertEquals("Black to move - Check", localStatusText(GameStatus.CHECK, ChessColor.BLACK))
        assertEquals("Checkmate - White wins", localStatusText(GameStatus.CHECKMATE, ChessColor.BLACK))
        assertEquals("Stalemate", localStatusText(GameStatus.STALEMATE, ChessColor.WHITE))
    }

    @Test
    fun onlineStatusTexts() {
        assertEquals("Your move", onlineStatusText(GameStatus.IN_PROGRESS, true, false, null))
        assertEquals("Opponent's move - Check", onlineStatusText(GameStatus.CHECK, false, false, null))
        assertEquals("Waiting for opponent", onlineStatusText(GameStatus.IN_PROGRESS, true, true, null))
        assertEquals("Draw - draw agreed", onlineStatusText(GameStatus.IN_PROGRESS, true, false, "Draw - draw agreed"))
    }

    @Test
    fun resultTextsDependOnMySide() {
        val whiteWins = GameResult(Side.WHITE, ResultReason.TIMEOUT)
        assertEquals("You won - time ran out", resultText(whiteWins, Side.WHITE))
        assertEquals("You lost - time ran out", resultText(whiteWins, Side.BLACK))
        assertEquals("Draw - stalemate", resultText(GameResult(null, ResultReason.STALEMATE), Side.WHITE))
    }

    @Test
    fun clockFormatting() {
        assertEquals("1:00", formatClock(60_000))
        assertEquals("4:05", formatClock(244_500))
        assertEquals("0:10", formatClock(10_000))
        assertEquals("0:09.4", formatClock(9_450))
        assertEquals("0:00.0", formatClock(-5))
    }

    @Test
    fun codeInputIsUppercasedFilteredAndTrimmed() {
        assertEquals("ABC234", sanitizeCode("abc-234zz"))
        assertEquals("AB", sanitizeCode("a0b1"))
    }
}
