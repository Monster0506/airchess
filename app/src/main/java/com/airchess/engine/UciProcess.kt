package com.airchess.engine

import java.io.InputStream
import java.io.OutputStream

/** Child-process transport: UciEngine reads it on its IO dispatcher, so blocking is fine. */
internal class StreamUciProcess(
    input: InputStream,
    private val output: OutputStream,
    private val onDestroy: () -> Unit,
) : UciProcess {
    private val reader = input.bufferedReader(Charsets.UTF_8)
    private val writeLock = Any()

    override fun writeLine(line: String) {
        synchronized(writeLock) {
            output.write((line + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    override suspend fun readLine(): String? = reader.readLine()

    override fun destroy() {
        onDestroy()
    }

    companion object {
        fun launch(executable: String): StreamUciProcess {
            val process = ProcessBuilder(executable).redirectErrorStream(true).start()
            return StreamUciProcess(process.inputStream, process.outputStream) {
                process.destroy()
            }
        }
    }
}
