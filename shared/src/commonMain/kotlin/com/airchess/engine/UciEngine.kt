package com.airchess.engine

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException

/**
 * Drives a UCI engine process. Calls are serialized; the process starts on first use
 * and restarts on the next call if it died.
 */
class UciEngine(
    private val launcher: () -> UciProcess,
    private val options: Map<String, String> = emptyMap(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val handshakeTimeoutMs: Long = HANDSHAKE_TIMEOUT_MS,
    private val stopTimeoutMs: Long = STOP_TIMEOUT_MS,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Volatile private var session: Session? = null
    @Volatile private var closed = false

    suspend fun analyze(fen: String, depth: Int): EngineAnalysis {
        require(depth > 0) { "depth must be positive" }
        return mutex.withLock {
            withContext(ioDispatcher) { search(fen, depth) }
        }
    }

    /** Clears engine state (hash tables) between unrelated games. */
    suspend fun newGame() {
        mutex.withLock {
            withContext(ioDispatcher) {
                val active = session ?: return@withContext
                try {
                    active.send("ucinewgame")
                    withTimeout(handshakeTimeoutMs) { active.sync() }
                } catch (e: TimeoutCancellationException) {
                    fail(active, "Engine did not answer isready", e)
                } catch (e: IOException) {
                    fail(active, "Engine connection lost", e)
                }
            }
        }
    }

    fun close() {
        closed = true
        val active = session
        session = null
        if (active != null) {
            try {
                active.send("quit")
            } catch (_: IOException) {
                // Process already gone; destroy below is enough.
            }
            active.process.destroy()
        }
        scope.cancel()
    }

    private suspend fun search(fen: String, depth: Int): EngineAnalysis {
        val active = ensureStarted()
        try {
            active.send("position fen $fen")
            active.send("go depth $depth")
            return collect(active)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { abort(active) }
            throw e
        } catch (e: IOException) {
            fail(active, "Engine connection lost", e)
        }
    }

    private suspend fun collect(active: Session): EngineAnalysis {
        var last: UciInfo? = null
        while (true) {
            val line = active.receive() ?: fail(active, "Engine exited during search")
            val best = UciParser.parseBestMove(line)
            if (best != null) {
                return EngineAnalysis(
                    bestMove = best.move,
                    evaluation = last?.score,
                    principalVariation = last?.principalVariation.orEmpty(),
                    depth = last?.depth ?: 0,
                )
            }
            val info = UciParser.parseInfo(line)
            if (info != null && !info.isBound) {
                last = info
            }
        }
    }

    /** Stops a running search and consumes its bestmove so the next call starts clean. */
    private suspend fun abort(active: Session) {
        try {
            active.send("stop")
            val drained = withTimeoutOrNull(stopTimeoutMs) { drainToBestMove(active) }
            if (drained != true) {
                discard(active)
            }
        } catch (_: IOException) {
            discard(active)
        }
    }

    private suspend fun drainToBestMove(active: Session): Boolean {
        while (true) {
            val line = active.receive() ?: return false
            if (UciParser.parseBestMove(line) != null) {
                return true
            }
        }
    }

    private suspend fun ensureStarted(): Session {
        if (closed) {
            throw EngineUnavailableException("Engine is closed")
        }
        session?.let { return it }

        val process = try {
            launcher()
        } catch (e: IOException) {
            throw EngineUnavailableException("Cannot start engine: ${e.message}", e)
        } catch (e: SecurityException) {
            throw EngineUnavailableException("Cannot start engine: ${e.message}", e)
        }
        val started = Session(process, startPump(process))
        try {
            withTimeout(handshakeTimeoutMs) { handshake(started) }
        } catch (e: TimeoutCancellationException) {
            discard(started)
            throw EngineUnavailableException("Engine handshake timed out", e)
        } catch (e: IOException) {
            discard(started)
            throw EngineUnavailableException("Engine handshake failed: ${e.message}", e)
        } catch (e: CancellationException) {
            discard(started)
            throw e
        } catch (e: EngineUnavailableException) {
            discard(started)
            throw e
        }
        session = started
        return started
    }

    private suspend fun handshake(started: Session) {
        started.send("uci")
        started.await("uciok")
        for ((name, value) in options) {
            started.send("setoption name $name value $value")
        }
        started.send("ucinewgame")
        started.sync()
    }

    private fun startPump(process: UciProcess): Channel<String> {
        val lines = Channel<String>(Channel.UNLIMITED)
        scope.launch {
            try {
                while (true) {
                    val line = process.readLine() ?: break
                    lines.trySend(line)
                }
            } catch (_: IOException) {
                // Stream closed by destroy(); treated as end of output.
            } finally {
                lines.close()
            }
        }
        return lines
    }

    private fun discard(active: Session) {
        if (session === active) {
            session = null
        }
        active.process.destroy()
    }

    private fun fail(active: Session, message: String, cause: Throwable? = null): Nothing {
        discard(active)
        throw EngineUnavailableException(message, cause)
    }

    private class Session(val process: UciProcess, private val lines: Channel<String>) {
        fun send(command: String) {
            process.writeLine(command)
        }

        /** Null when the engine closed its output. */
        suspend fun receive(): String? = try {
            lines.receive()
        } catch (_: ClosedReceiveChannelException) {
            null
        }

        suspend fun await(expected: String) {
            while (true) {
                val line = receive() ?: throw EngineUnavailableException("Engine exited during handshake")
                if (line.trim() == expected) {
                    return
                }
            }
        }

        suspend fun sync() {
            send("isready")
            await("readyok")
        }
    }

    companion object {
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
