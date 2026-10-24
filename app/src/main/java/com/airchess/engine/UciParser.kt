package com.airchess.engine

/** One principal-variation report from the engine ("info ... score ... pv ..."). */
internal data class UciInfo(
    val depth: Int,
    val score: EngineScore,
    val principalVariation: List<String>,
    /** True for "lowerbound"/"upperbound" scores, which are not final for their depth. */
    val isBound: Boolean,
)

/** [move] is null for "bestmove (none)", i.e. the side to move has no legal move. */
internal data class UciBestMove(val move: String?, val ponder: String?)

internal object UciParser {
    private const val INFO = "info"
    private const val BESTMOVE = "bestmove"
    private const val NO_MOVE = "(none)"

    /**
     * Returns null for non-info lines, lines without a score (e.g. "info string"),
     * and secondary lines when MultiPV > 1.
     */
    fun parseInfo(line: String): UciInfo? {
        val tokens = line.trim().split(WHITESPACE)
        if (tokens.firstOrNull() != INFO) {
            return null
        }

        var depth = 0
        var score: EngineScore? = null
        var isBound = false
        var pv = emptyList<String>()
        var i = 1
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> {
                    depth = tokens.getOrNull(i + 1)?.toIntOrNull() ?: return null
                    i += 2
                }
                "multipv" -> {
                    val index = tokens.getOrNull(i + 1)?.toIntOrNull() ?: return null
                    if (index != 1) {
                        return null
                    }
                    i += 2
                }
                "score" -> {
                    val value = tokens.getOrNull(i + 2)?.toIntOrNull() ?: return null
                    score = when (tokens.getOrNull(i + 1)) {
                        "cp" -> EngineScore.Centipawns(value)
                        "mate" -> EngineScore.Mate(value)
                        else -> return null
                    }
                    i += 3
                }
                "lowerbound", "upperbound" -> {
                    isBound = true
                    i += 1
                }
                "pv" -> {
                    pv = tokens.subList(i + 1, tokens.size).filter { it.isNotEmpty() }
                    i = tokens.size
                }
                "string" -> return null
                else -> i += 1
            }
        }

        return UciInfo(depth, score ?: return null, pv, isBound)
    }

    /** Returns null when [line] is not a "bestmove" line. */
    fun parseBestMove(line: String): UciBestMove? {
        val tokens = line.trim().split(WHITESPACE)
        if (tokens.firstOrNull() != BESTMOVE) {
            return null
        }

        val move = tokens.getOrNull(1)?.takeIf { it != NO_MOVE && it.isNotEmpty() }
        val ponder = if (tokens.getOrNull(2) == "ponder") tokens.getOrNull(3) else null
        return UciBestMove(move, ponder)
    }

    private val WHITESPACE = Regex("\\s+")
}
