package com.airchess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UciParserTest {
    @Test
    fun parsesCentipawnScoreAndPv() {
        val info = UciParser.parseInfo(
            "info depth 12 seldepth 18 multipv 1 score cp 34 nodes 1000 nps 500000 " +
                "hashfull 10 tbhits 0 time 2 pv e2e4 e7e5 g1f3",
        )

        assertNotNull(info)
        assertEquals(12, info!!.depth)
        assertEquals(EngineScore.Centipawns(34), info.score)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), info.principalVariation)
        assertFalse(info.isBound)
    }

    @Test
    fun parsesNegativeCentipawns() {
        val info = UciParser.parseInfo("info depth 5 score cp -120 pv d7d5")

        assertEquals(EngineScore.Centipawns(-120), info!!.score)
    }

    @Test
    fun parsesMateScores() {
        val win = UciParser.parseInfo("info depth 9 score mate 3 pv d1h5")
        val loss = UciParser.parseInfo("info depth 9 score mate -2 pv a7a6")

        assertEquals(EngineScore.Mate(3), win!!.score)
        assertEquals(EngineScore.Mate(-2), loss!!.score)
    }

    @Test
    fun flagsBoundScores() {
        val lower = UciParser.parseInfo("info depth 10 score cp 50 lowerbound pv e2e4")
        val upper = UciParser.parseInfo("info depth 10 score cp -50 upperbound pv e2e4")

        assertTrue(lower!!.isBound)
        assertEquals(EngineScore.Centipawns(50), lower.score)
        assertTrue(upper!!.isBound)
    }

    @Test
    fun acceptsMissingPv() {
        val info = UciParser.parseInfo("info depth 1 seldepth 0 multipv 1 score cp 0 nodes 0 pv ")

        assertEquals(emptyList<String>(), info!!.principalVariation)
    }

    @Test
    fun ignoresLinesWithoutScore() {
        assertNull(UciParser.parseInfo("info depth 3 currmove e2e4 currmovenumber 1"))
        assertNull(UciParser.parseInfo("info string NNUE evaluation using nn.nnue score cp 5"))
        assertNull(UciParser.parseInfo("info nodes 100 nps 2000"))
    }

    @Test
    fun ignoresSecondaryMultiPv() {
        assertNull(UciParser.parseInfo("info depth 8 multipv 2 score cp 10 pv d2d4"))
        assertNotNull(UciParser.parseInfo("info depth 8 multipv 1 score cp 10 pv e2e4"))
    }

    @Test
    fun ignoresNonInfoLines() {
        assertNull(UciParser.parseInfo("bestmove e2e4"))
        assertNull(UciParser.parseInfo("readyok"))
        assertNull(UciParser.parseInfo(""))
    }

    @Test
    fun rejectsMalformedScore() {
        assertNull(UciParser.parseInfo("info depth 3 score cp"))
        assertNull(UciParser.parseInfo("info depth 3 score cp abc pv e2e4"))
        assertNull(UciParser.parseInfo("info depth 3 score wdl 1 pv e2e4"))
    }

    @Test
    fun parsesBestMoveWithoutPonder() {
        assertEquals(UciBestMove("e2e4", null), UciParser.parseBestMove("bestmove e2e4"))
    }

    @Test
    fun parsesBestMoveWithPonder() {
        assertEquals(
            UciBestMove("g1f3", "g8f6"),
            UciParser.parseBestMove("bestmove g1f3 ponder g8f6"),
        )
    }

    @Test
    fun parsesPromotionBestMove() {
        assertEquals(UciBestMove("e7e8q", null), UciParser.parseBestMove("bestmove e7e8q"))
    }

    @Test
    fun bestMoveNoneIsNull() {
        assertEquals(UciBestMove(null, null), UciParser.parseBestMove("bestmove (none)"))
    }

    @Test
    fun nonBestMoveLineIsNull() {
        assertNull(UciParser.parseBestMove("info depth 1 score cp 0"))
        assertNull(UciParser.parseBestMove("uciok"))
    }
}
