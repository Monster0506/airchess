@file:OptIn(ExperimentalAtomicApi::class)

package com.airchess.engine

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import platform.Foundation.NSProcessInfo

/**
 * Stockfish linked into the app and run on its own thread. Closing releases the engine;
 * the next [analyze] starts it again.
 */
class IosStockfishEngine : ChessEngine {
    private val engine = AtomicReference<UciEngine?>(null)

    override suspend fun analyze(fen: String, depth: Int): EngineAnalysis =
        current().analyze(fen, depth)

    override fun close() {
        engine.exchange(null)?.close()
    }

    private fun current(): UciEngine {
        while (true) {
            engine.load()?.let { return it }

            val fresh = create()
            if (engine.compareAndSet(null, fresh)) {
                return fresh
            }
            fresh.close()
        }
    }

    private fun create() = UciEngine(
        launcher = { StockfishProcess.launch() },
        options = mapOf("Hash" to HASH_MB, "Threads" to threads().toString()),
    )

    private fun threads(): Int =
        NSProcessInfo.processInfo.activeProcessorCount.toInt().coerceIn(1, MAX_THREADS)

    private companion object {
        const val HASH_MB = "32"
        const val MAX_THREADS = 4
    }
}
