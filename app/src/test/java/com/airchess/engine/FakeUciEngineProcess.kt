package com.airchess.engine

import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Scripted UCI engine on its own thread, wired to the client through piped streams.
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

    val commands: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val searchStarted = CountDownLatch(1)
    val destroyed = CountDownLatch(1)

    // Client writes -> engine reads.
    private val toEngine = PipedOutputStream()
    private val engineIn = PipedInputStream(toEngine, PIPE_SIZE)
    private val reader = engineIn.bufferedReader()

    // Engine writes -> client reads.
    private val engineOut = PipedOutputStream()
    private val toClient = PipedInputStream(engineOut, PIPE_SIZE)

    private var searches = 0

    private val engineThread = thread(isDaemon = true, name = "fake-uci-engine") { run() }

    fun asProcess(): UciProcess = StreamUciProcess(toClient, toEngine) {
        destroyed.countDown()
        runCatching { engineOut.close() }
        runCatching { toEngine.close() }
    }

    fun awaitExit() {
        engineThread.join(TimeUnit.SECONDS.toMillis(5))
    }

    private fun reply(line: String) {
        engineOut.write((line + "\n").toByteArray())
        engineOut.flush()
    }

    private fun run() {
        try {
            while (true) {
                val command = reader.readLine() ?: break
                commands += command
                if (!handle(command)) {
                    break
                }
            }
        } catch (_: IOException) {
            // Pipe closed by destroy().
        } finally {
            runCatching { engineOut.close() }
        }
    }

    /** Returns false when the engine should exit. */
    private fun handle(command: String): Boolean {
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

    private fun search(): Boolean {
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
                searchStarted.countDown()
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

    private fun awaitStop() {
        while (true) {
            val command = reader.readLine() ?: return
            commands += command
            if (command == "stop") {
                return
            }
        }
    }

    private companion object {
        const val PIPE_SIZE = 1 shl 16
    }
}
