package com.airchess.ar

import com.airchess.chess.ChessSquare
import com.airchess.chess.PieceType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Maps a board-local ray to a square. Pure math: the caller moves the world ray into board-local
 * space first, so the black perspective (rotated content root) needs no special case here.
 *
 * Each piece is a tapered body of its model's height (wide base, narrow top); otherwise the
 * y = 0 board plane decides. The nearest hit wins, so tapping the visible body of a tall piece
 * selects that piece, while a tap just above a short piece's taller neighbour still reaches it.
 */
object BoardPicker {
    private const val BASE_RADIUS_FACTOR = 0.3f
    private const val TOP_TAPER = 0.4f
    private const val SAMPLES = 24
    private const val EPS = 1e-6f

    /** Model heights in meters, matching the generated GLB assets. */
    fun height(type: PieceType): Float = when (type) {
        PieceType.PAWN -> 0.022f
        PieceType.ROOK -> 0.028f
        PieceType.KNIGHT -> 0.032f
        PieceType.BISHOP -> 0.036f
        PieceType.QUEEN -> 0.042f
        PieceType.KING -> 0.046f
    }

    fun pick(
        geometry: BoardGeometry,
        origin: Vec3,
        direction: Vec3,
        pieces: Map<ChessSquare, PieceType>,
    ): ChessSquare? {
        var bestSquare: ChessSquare? = null
        var bestT = Float.MAX_VALUE

        if (abs(direction.y) > EPS) {
            val t = -origin.y / direction.y
            val hit = if (t >= 0f) {
                geometry.localPositionToSquare(origin.x + t * direction.x, origin.z + t * direction.z)
            } else {
                null
            }
            if (hit != null) {
                bestSquare = hit
                bestT = t
            }
        }

        val baseRadius = geometry.squareSize * BASE_RADIUS_FACTOR
        pieces.forEach { (square, type) ->
            val center = geometry.squareToLocalPosition(square)
            val t = bodyHit(origin, direction, center.x, center.z, height(type), baseRadius)
            if (t != null && t < bestT) {
                bestT = t
                bestSquare = square
            }
        }

        return bestSquare
    }

    /** Smallest ray parameter inside the tapered body, found by sampling its bounding cylinder. */
    private fun bodyHit(
        origin: Vec3,
        direction: Vec3,
        cx: Float,
        cz: Float,
        height: Float,
        baseRadius: Float,
    ): Float? {
        var start = 0f
        var end = Float.MAX_VALUE

        if (abs(direction.y) > EPS) {
            val atBase = -origin.y / direction.y
            val atTop = (height - origin.y) / direction.y
            start = max(start, min(atBase, atTop))
            end = min(end, max(atBase, atTop))
        } else if (origin.y < 0f || origin.y > height) {
            return null
        }

        val dx = origin.x - cx
        val dz = origin.z - cz
        val a = direction.x * direction.x + direction.z * direction.z
        if (a > EPS) {
            val b = 2f * (dx * direction.x + dz * direction.z)
            val c = dx * dx + dz * dz - baseRadius * baseRadius
            val discriminant = b * b - 4f * a * c
            if (discriminant < 0f) {
                return null
            }
            val root = sqrt(discriminant)
            start = max(start, (-b - root) / (2f * a))
            end = min(end, (-b + root) / (2f * a))
        } else if (dx * dx + dz * dz > baseRadius * baseRadius) {
            return null
        }

        if (start > end || end == Float.MAX_VALUE) {
            return null
        }

        for (i in 0..SAMPLES) {
            val t = start + (end - start) * i / SAMPLES
            val y = origin.y + t * direction.y
            val radius = baseRadius * (1f - TOP_TAPER * y / height)
            val hx = origin.x + t * direction.x - cx
            val hz = origin.z + t * direction.z - cz
            if (hx * hx + hz * hz <= radius * radius) {
                return t
            }
        }
        return null
    }
}
