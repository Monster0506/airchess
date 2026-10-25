@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class, DelicateCoroutinesApi::class)

package com.airchess.engine

import com.airchess.engine.bridge.sf_read_line
import com.airchess.engine.bridge.sf_send
import com.airchess.engine.bridge.sf_start
import com.airchess.engine.bridge.sf_stop
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import platform.posix.usleep

/**
 * The in-process Stockfish of the bridge library. The bridge is a process-wide singleton, so
 * at most one instance is live: launching replaces the previous one.
 */
internal class StockfishProcess private constructor() : UciProcess {
    private val destroyed = AtomicBoolean(false)
    private val reading = AtomicInt(IDLE)

    override fun writeLine(line: String) {
        if (destroyed.load() || sf_send(line) != BRIDGE_OK) {
            throw IOException("Stockfish is not running")
        }
    }

    override suspend fun readLine(): String? {
        if (destroyed.load()) {
            return null
        }
        return withContext(readerDispatcher) { poll() }
    }

    override fun destroy() {
        if (!destroyed.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }
        if (current.compareAndSet(this, null)) {
            sf_stop()
        }
        // The next sf_start must not race a reader still inside the bridge.
        while (reading.load() != IDLE) {
            usleep(SPIN_MICROS)
        }
    }

    private fun CoroutineScope.poll(): String? {
        memScoped {
            val buffer = allocArray<ByteVar>(LINE_CAPACITY)
            while (true) {
                ensureActive()
                reading.store(BUSY)
                if (destroyed.load()) {
                    reading.store(IDLE)
                    return null
                }
                val length = sf_read_line(buffer, LINE_CAPACITY, POLL_TIMEOUT_MS)
                reading.store(IDLE)

                if (length < 0) {
                    return null
                }
                if (length > 0) {
                    return buffer.toKString()
                }
            }
        }
    }

    companion object {
        private const val BRIDGE_OK = 0
        private const val BRIDGE_RUNNING = -1
        private const val LINE_CAPACITY = 8192
        private const val POLL_TIMEOUT_MS = 100
        private const val SPIN_MICROS = 1_000u
        private const val IDLE = 0
        private const val BUSY = 1

        private val current = AtomicReference<StockfishProcess?>(null)

        // One long-lived thread: sf_read_line blocks, and the bridge allows a single reader.
        private val readerDispatcher by lazy { newSingleThreadContext("airchess-stockfish-reader") }

        fun launch(): StockfishProcess {
            current.load()?.destroy()

            var status = sf_start()
            if (status == BRIDGE_RUNNING) {
                sf_stop()
                status = sf_start()
            }
            if (status != BRIDGE_OK) {
                throw EngineUnavailableException("Cannot start Stockfish (bridge status $status)")
            }

            val process = StockfishProcess()
            current.store(process)
            return process
        }
    }
}
