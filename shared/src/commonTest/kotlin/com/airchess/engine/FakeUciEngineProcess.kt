package com.airchess.engine

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException

/**
 * Scripted UCI engine running as its own coroutine, wired to the client through channels.
 * [searchMode] decides how "go" is answered.
 */
internal class FakeUciEngineProcess(
    private val searchMode: SearchMode = SearchMode.IMMEDIATE,
    private val answersUci: Boolean = true,
) {
    enum class SearchMode {
        /** Emits infos and bestmove right away. */
        IMMEDIATE,

        /** First search emits one info and waits for "stop"; later searches answer immediately. */
        UNTIL_STOP,

        /** Emits one info, then closes its output as if it crashed. */
        CRASH,

        /** Emits only "bestmove (none)", like a checkmated position. */
        NO_MOVES,
    }

    /** Only the engine coroutine appends; readers see whole snapshots. */
    @Volatile
    var commands: List<String> = emptyList()
        private set

    val searchStarted = CompletableDeferred<Unit>()
    val destroyed = CompletableDeferred<Unit>()

    private val toEngine = Channel<String>(Channel.UNLIMITED)
    private val toClient = Channel<String>(Channel.UNLIMITED)

    private var searches = 0

    private val engineJob = CoroutineScope(Dispatchers.Default).launch { run() }

    fun asProcess(): UciProcess = object : UciProcess {
        override fun writeLine(line: String) {
            if (toEngine.trySend(line).isFailure) {
                throw IOException("Pipe closed")
            }
        }

        override suspend fun readLine(): String? = toClient.receiveCatching().getOrNull()

        override fun destroy() {
            destroyed.complete(Unit)
            toClient.close()
            toEngine.close()
        }
    }

    suspend fun awaitExit() {
        withTimeoutOrNull(EXIT_TIMEOUT_MS) { engineJob.join() }
    }

    private fun reply(line: String) {
        toClient.trySend(line)
    }

    private suspend fun run() {
        try {
            for (command in toEngine) {
                record(command)
                if (!handle(command)) {
                    break
                }
            }
        } finally {
            toClient.close()
        }
    }

    private fun record(command: String) {
        commands = commands + command
    }

    /** Returns false when the engine should exit. */
    private suspend fun handle(command: String): Boolean {
        when {
            command == "uci" && answersUci -> {
                reply("id name FakeFish")
                reply("option name Hash type spin default 16 min 1 max 1024")
                reply("uciok")
            }
            command == "isready" -> reply("readyok")
            command.startsWith("go ") -> return search()
            command == "quit" -> return false
        }
        return true
    }

    private suspend fun search(): Boolean {
        val mode = if (searchMode == SearchMode.UNTIL_STOP && searches++ > 0) {
            SearchMode.IMMEDIATE
        } else {
            searchMode
        }
        when (mode) {
            SearchMode.IMMEDIATE -> {
                reply("info string searching")
                reply("info depth 1 seldepth 1 multipv 1 score cp 10 pv e2e4")
                reply("info depth 2 multipv 2 score cp 5 pv d2d4")
                reply("info depth 2 seldepth 3 multipv 1 score cp 30 lowerbound pv e2e4 e7e5")
                reply("info depth 2 seldepth 3 multipv 1 score cp 25 pv e2e4 e7e5")
                reply("info depth 3 seldepth 4 multipv 1 score mate -2 pv e2e4 e7e5 d1h5")
                reply("bestmove e2e4 ponder e7e5")
            }
            SearchMode.UNTIL_STOP -> {
                reply("info depth 4 multipv 1 score cp 12 pv g1f3")
                searchStarted.complete(Unit)
                awaitStop()
                reply("bestmove g1f3")
            }
            SearchMode.CRASH -> {
                reply("info depth 1 multipv 1 score cp 0 pv e2e4")
                return false
            }
            SearchMode.NO_MOVES -> reply("bestmove (none)")
        }
        return true
    }

    private suspend fun awaitStop() {
        for (command in toEngine) {
            record(command)
            if (command == "stop") {
                return
            }
        }
    }

    private companion object {
        const val EXIT_TIMEOUT_MS = 5_000L
    }
}

/** True when the signal fired within [millis]. */
internal suspend fun CompletableDeferred<Unit>.within(millis: Long): Boolean =
    withTimeoutOrNull(millis) { await() } != null
