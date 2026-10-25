package com.airchess.ar

import com.airchess.chess.ChessSquare
import kotlin.math.floor

/**
 * Board-local coordinates: origin at the board center, top surface at y = 0, files a..h run
 * along -X..+X and ranks 1..8 along +Z..-Z (white sits at +Z). Orientation-free: the black
 * perspective is a rotation of the content root, never a change here.
 */
data class BoardGeometry(val boardSize: Float = DEFAULT_BOARD_SIZE) {

    val squareSize: Float
        get() = boardSize / ChessSquare.BOARD_SIZE

    private val halfBoard: Float
        get() = boardSize / 2f

    /** Center of [square] on the board surface (y = 0). */
    fun squareToLocalPosition(square: ChessSquare): Vec3 = Vec3(
        x = -halfBoard + (square.file + HALF) * squareSize,
        y = 0f,
        z = halfBoard - (square.rank + HALF) * squareSize,
    )

    /** Square under the local point (x, z), or null outside the board. */
    fun localPositionToSquare(x: Float, z: Float): ChessSquare? {
        if (x < -halfBoard - EDGE_EPSILON || x > halfBoard + EDGE_EPSILON) {
            return null
        }
        if (z < -halfBoard - EDGE_EPSILON || z > halfBoard + EDGE_EPSILON) {
            return null
        }

        val file = floor((x + halfBoard) / squareSize).toInt().coerceIn(0, LAST_INDEX)
        val rank = floor((halfBoard - z) / squareSize).toInt().coerceIn(0, LAST_INDEX)

        return ChessSquare.at(file, rank)
    }

    companion object {
        const val DEFAULT_BOARD_SIZE = 0.40f

        private const val HALF = 0.5f
        private const val LAST_INDEX = ChessSquare.BOARD_SIZE - 1
        private const val EDGE_EPSILON = 1e-6f
    }
}
