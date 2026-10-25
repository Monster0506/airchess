package com.airchess.engine

/** Engine score from the side-to-move's perspective, as reported by UCI "score". */
sealed interface EngineScore {
    data class Centipawns(val value: Int) : EngineScore

    /** Positive: side to move mates in [moves]; negative: gets mated. */
    data class Mate(val moves: Int) : EngineScore
}

data class EngineAnalysis(
    /** UCI move, e.g. "g1f3"; null when the position has no legal moves. */
    val bestMove: String?,
    val evaluation: EngineScore?,
    val principalVariation: List<String>,
    val depth: Int,
)

interface ChessEngine {
    suspend fun analyze(fen: String, depth: Int): EngineAnalysis

    fun close()
}
