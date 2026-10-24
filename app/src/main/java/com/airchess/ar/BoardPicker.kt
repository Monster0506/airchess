package com.airchess.ar

import com.airchess.chess.ChessSquare
import dev.romainguy.kotlin.math.Float3
import kotlin.math.sqrt

/**
 * Maps a board-local ray to a square. Pure math: the caller moves the world ray into board-local
 * space first, so the black perspective (rotated content root) needs no special case here.
 *
 * A ray counts as hitting a piece when it crosses a vertical cylinder around an occupied square
 * center (y in 0..[PIECE_HEIGHT]); otherwise the y = 0 board plane decides. The nearest hit wins,
 * so tapping the visible body of a tall piece selects that piece, not the square behind it.
 */
object BoardPicker {
    const val PIECE_HEIGHT = 0.045f
    const val PIECE_RADIUS_FACTOR = 0.45f

    private const val EPS = 1e-6f

    fun pick(
        geometry: BoardGeometry,
        origin: Float3,
        direction: Float3,
        occupied: Collection<ChessSquare>,
    ): ChessSquare? {
        var bestSquare: ChessSquare? = null
        var bestT = Float.MAX_VALUE

        if (kotlin.math.abs(direction.y) > EPS) {
            val t = -origin.y / direction.y
            if (t >= 0f) {
                val hit = geometry.localPositionToSquare(origin.x + t * direction.x, origin.z + t * direction.z)
                if (hit != null) {
                    bestSquare = hit
                    bestT = t
                }
            }
        }

        val radius = geometry.squareSize * PIECE_RADIUS_FACTOR
        occupied.forEach { square ->
            val center = geometry.squareToLocalPosition(square)
            val t = cylinderHit(origin, direction, center.x, center.z, radius)
            if (t != null && t < bestT) {
                bestT = t
                bestSquare = square
            }
        }

        return bestSquare
    }

    /** Smallest non-negative ray parameter hitting the finite cylinder, or null. */
    private fun cylinderHit(origin: Float3, direction: Float3, cx: Float, cz: Float, radius: Float): Float? {
        var best: Float? = null

        val dx = origin.x - cx
        val dz = origin.z - cz
        val a = direction.x * direction.x + direction.z * direction.z
        if (a > EPS) {
            val b = 2f * (dx * direction.x + dz * direction.z)
            val c = dx * dx + dz * dz - radius * radius
            val discriminant = b * b - 4f * a * c
            if (discriminant >= 0f) {
                val root = sqrt(discriminant)
                val near = (-b - root) / (2f * a)
                val far = (-b + root) / (2f * a)
                for (t in floatArrayOf(near, far)) {
                    val y = origin.y + t * direction.y
                    if (t >= 0f && y >= 0f && y <= PIECE_HEIGHT) {
                        best = t
                        break
                    }
                }
            }
        }

        if (kotlin.math.abs(direction.y) > EPS) {
            val t = (PIECE_HEIGHT - origin.y) / direction.y
            if (t >= 0f) {
                val hx = origin.x + t * direction.x - cx
                val hz = origin.z + t * direction.z - cz
                if (hx * hx + hz * hz <= radius * radius && (best == null || t < best)) {
                    best = t
                }
            }
        }

        return best
    }
}
