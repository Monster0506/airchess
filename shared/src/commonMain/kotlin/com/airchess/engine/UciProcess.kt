package com.airchess.engine

/**
 * Line-oriented view of a running UCI engine, so each platform and the tests can supply
 * their own transport. [UciEngine] calls it from its IO dispatcher, so [readLine] may block.
 */
interface UciProcess {
    /** Throws [kotlinx.io.IOException] when the engine is gone. */
    fun writeLine(line: String)

    /** Null once the engine closed its output. */
    suspend fun readLine(): String?

    fun destroy()
}
