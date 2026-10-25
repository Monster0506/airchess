package com.airchess.engine

import com.airchess.chess.ChessRules
import com.airchess.chess.Fen
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** Runs the real Stockfish from the linked xcframework inside the iOS simulator. */
class IosStockfishEngineTest {
    private val engine = IosStockfishEngine()

    @AfterTest
    fun tearDown() {
        engine.close()
    }

    private fun runReal(body: suspend CoroutineScope.() -> Unit) =
        runTest(timeout = TEST_TIMEOUT) { withContext(Dispatchers.Default, body) }

    @Test
    fun startPositionGivesLegalMove() = runReal {
        val analysis = engine.analyze(START_FEN, DEPTH)

        val legal = ChessRules.legalMoves(Fen.parse(START_FEN)).map { it.uci }
        assertTrue(analysis.bestMove in legal, "bestMove ${analysis.bestMove} is not legal")
        assertNotNull(analysis.evaluation)
        assertTrue(analysis.depth >= DEPTH, "depth ${analysis.depth}")
        assertTrue(analysis.principalVariation.isNotEmpty())
    }

    @Test
    fun mateInOneIsFound() = runReal {
        val analysis = engine.analyze(MATE_IN_ONE_FEN, DEPTH)

        assertEquals("a1a8", analysis.bestMove)
        assertEquals(EngineScore.Mate(1), analysis.evaluation)
    }

    @Test
    fun closeThenAnalyzeRestarts() = runReal {
        val first = engine.analyze(MATE_IN_ONE_FEN, DEPTH)
        assertEquals("a1a8", first.bestMove)

        engine.close()

        val second = engine.analyze(MATE_IN_ONE_FEN, DEPTH)
        assertEquals("a1a8", second.bestMove)
        assertEquals(EngineScore.Mate(1), second.evaluation)
    }

    @Test
    fun secondInstanceTakesOverTheBridge() = runReal {
        engine.analyze(START_FEN, DEPTH)

        val other = IosStockfishEngine()
        try {
            assertEquals("a1a8", other.analyze(MATE_IN_ONE_FEN, DEPTH).bestMove)
        } finally {
            other.close()
        }
    }

    private companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        const val MATE_IN_ONE_FEN = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"
        const val DEPTH = 10
        val TEST_TIMEOUT = 120.seconds
    }
}
