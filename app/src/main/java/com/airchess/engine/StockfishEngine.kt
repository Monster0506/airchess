package com.airchess.engine

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Stockfish shipped as libstockfish.so and executed from the app's native library
 * directory (extracted because the APK uses legacy JNI packaging).
 */
class StockfishEngine(context: Context) : ChessEngine {
    private val binary = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    private val engine by lazy {
        UciEngine(
            launcher = ::launch,
            options = mapOf("Hash" to HASH_MB, "Threads" to THREADS),
        )
    }

    override suspend fun analyze(fen: String, depth: Int): EngineAnalysis =
        engine.analyze(fen, depth)

    override fun close() {
        engine.close()
    }

    private fun launch(): UciProcess {
        if (!binary.isFile) {
            throw EngineUnavailableException("Stockfish binary not found at ${binary.path}")
        }
        try {
            return StreamUciProcess.launch(binary.path)
        } catch (e: IOException) {
            throw EngineUnavailableException("Cannot start Stockfish: ${e.message}", e)
        } catch (e: SecurityException) {
            throw EngineUnavailableException("Cannot start Stockfish: ${e.message}", e)
        }
    }

    private companion object {
        const val BINARY_NAME = "libstockfish.so"
        const val HASH_MB = "32"
        const val THREADS = "2"
    }
}
