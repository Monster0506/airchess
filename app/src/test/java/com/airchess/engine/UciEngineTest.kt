package com.airchess.engine

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UciEngineTest {
    private val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    private fun engineFor(
        fake: FakeUciEngineProcess,
        options: Map<String, String> = emptyMap(),
        handshakeTimeoutMs: Long = 5_000,
    ) = UciEngine(
        launcher = { fake.asProcess() },
        options = options,
        ioDispatcher = Dispatchers.IO,
        handshakeTimeoutMs = handshakeTimeoutMs,
        stopTimeoutMs = 2_000,
    )

    @Test
    fun handshakeThenSearchSequence() = runBlocking {
        val fake = FakeUciEngineProcess()
        val engine = engineFor(fake, mapOf("Hash" to "32"))

        engine.analyze(fen, 12)

        assertEquals(
            listOf(
                "uci",
                "setoption name Hash value 32",
                "ucinewgame",
                "isready",
                "position fen $fen",
                "go depth 12",
            ),
            fake.commands.toList(),
        )
        engine.close()
    }

    @Test
    fun analyzeReturnsLastExactInfoAndBestMove() = runBlocking {
        val fake = FakeUciEngineProcess()
        val engine = engineFor(fake)

        val result = engine.analyze(fen, 3)

        assertEquals("e2e4", result.bestMove)
        assertEquals(EngineScore.Mate(-2), result.evaluation)
        assertEquals(listOf("e2e4", "e7e5", "d1h5"), result.principalVariation)
        assertEquals(3, result.depth)
        engine.close()
    }

    @Test
    fun bestMoveNoneYieldsNullMove() = runBlocking {
        val fake = FakeUciEngineProcess(FakeUciEngineProcess.SearchMode.NO_MOVES)
        val engine = engineFor(fake)

        val result = engine.analyze(fen, 5)

        assertNull(result.bestMove)
        assertNull(result.evaluation)
        assertEquals(0, result.depth)
        engine.close()
    }

    @Test
    fun handshakeHappensOnce() = runBlocking {
        val fake = FakeUciEngineProcess()
        val launches = AtomicInteger()
        val engine = UciEngine(launcher = { launches.incrementAndGet(); fake.asProcess() })

        engine.analyze(fen, 1)
        engine.analyze(fen, 2)

        assertEquals(1, launches.get())
        assertEquals(1, fake.commands.count { it == "uci" })
        assertEquals(2, fake.commands.count { it.startsWith("go ") })
        engine.close()
    }

    @Test
    fun concurrentAnalysesDoNotInterleave() = runBlocking {
        val fake = FakeUciEngineProcess()
        val engine = engineFor(fake)

        val jobs = List(4) { i -> async(Dispatchers.Default) { engine.analyze(fen, i + 1) } }
        jobs.forEach { it.await() }

        val search = fake.commands.filter { it.startsWith("position") || it.startsWith("go") }
        assertEquals(8, search.size)
        search.chunked(2).forEach { (position, go) ->
            assertTrue(position.startsWith("position fen"))
            assertTrue(go.startsWith("go depth"))
        }
        engine.close()
    }

    @Test
    fun cancellationSendsStopAndKeepsEngineUsable() = runBlocking {
        val fake = FakeUciEngineProcess(FakeUciEngineProcess.SearchMode.UNTIL_STOP)
        val engine = engineFor(fake)

        val job = async(Dispatchers.Default) { engine.analyze(fen, 99) }
        assertTrue(fake.searchStarted.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue(fake.commands.contains("stop"))
        assertFalse(fake.destroyed.await(100, TimeUnit.MILLISECONDS))

        val next = withTimeout(5_000) { engine.analyze(fen, 4) }
        assertEquals("e2e4", next.bestMove)
        engine.close()
    }

    @Test
    fun closeSendsQuitAndDestroysProcess() = runBlocking {
        val fake = FakeUciEngineProcess()
        val engine = engineFor(fake)
        engine.analyze(fen, 1)

        engine.close()
        fake.awaitExit()

        assertEquals("quit", fake.commands.last())
        assertTrue(fake.destroyed.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun analyzeAfterCloseIsUnavailable() = runBlocking {
        val engine = engineFor(FakeUciEngineProcess())
        engine.close()

        try {
            engine.analyze(fen, 1)
            fail("expected EngineUnavailableException")
        } catch (_: EngineUnavailableException) {
            // expected
        }
    }

    @Test
    fun handshakeTimeoutIsUnavailableAndDestroysProcess() = runBlocking {
        val fake = FakeUciEngineProcess(answersUci = false)
        val engine = engineFor(fake, handshakeTimeoutMs = 200)

        try {
            engine.analyze(fen, 1)
            fail("expected EngineUnavailableException")
        } catch (_: EngineUnavailableException) {
            // expected
        }

        assertTrue(fake.destroyed.await(1, TimeUnit.SECONDS))
        engine.close()
    }

    @Test
    fun launcherFailureIsUnavailable() = runBlocking {
        val engine = UciEngine(launcher = { throw java.io.IOException("no such file") })

        try {
            engine.analyze(fen, 1)
            fail("expected EngineUnavailableException")
        } catch (e: EngineUnavailableException) {
            assertTrue(e.message!!.contains("no such file"))
        }
        engine.close()
    }

    @Test
    fun crashDuringSearchThenRestart() = runBlocking {
        val crashing = FakeUciEngineProcess(FakeUciEngineProcess.SearchMode.CRASH)
        val healthy = FakeUciEngineProcess()
        val processes = ArrayDeque(listOf(crashing, healthy))
        val engine = UciEngine(launcher = { processes.removeFirst().asProcess() })

        try {
            engine.analyze(fen, 5)
            fail("expected EngineUnavailableException")
        } catch (_: EngineUnavailableException) {
            // expected
        }

        val result = engine.analyze(fen, 3)
        assertEquals("e2e4", result.bestMove)
        engine.close()
    }
}
