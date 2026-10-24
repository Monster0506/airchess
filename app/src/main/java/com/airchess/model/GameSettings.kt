package com.airchess.model

data class GameSettings(
    val engineDepth: Int = 14,
    val showLegalMoves: Boolean = true,
    val showThreats: Boolean = false,
    val showDebug: Boolean = false,
    val hapticsEnabled: Boolean = true,
) {
    companion object {
        const val MIN_DEPTH = 8
        const val MAX_DEPTH = 20
    }
}
