package com.airchess.ar

import dev.romainguy.kotlin.math.Float3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A flat bar lying on the board: center, length along its own X axis, yaw about +Y in degrees. */
data class ArrowSegment(val centerX: Float, val centerZ: Float, val length: Float, val yawDegrees: Float)

/** Builds the hint arrow (one shaft plus two angled head bars) from square centers. */
object ArrowLayout {
    private const val START_INSET = 0.30f
    private const val TIP_INSET = 0.18f
    private const val HEAD_LENGTH = 0.36f
    private const val HEAD_SPREAD_DEGREES = 32.0
    private const val RAD_TO_DEG = 180.0 / Math.PI
    private const val DEG_TO_RAD = Math.PI / 180.0

    /** Bar thickness measured across its length, as a fraction of the square. */
    const val BAR_WIDTH = 0.11f

    fun segments(from: Float3, to: Float3, squareSize: Float): List<ArrowSegment> {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance < squareSize * 0.01f) {
            return emptyList()
        }

        val ux = dx / distance
        val uz = dz / distance
        val startX = from.x + ux * squareSize * START_INSET
        val startZ = from.z + uz * squareSize * START_INSET

        val headLength = squareSize * HEAD_LENGTH
        val shaftLength = (distance - squareSize * (START_INSET + TIP_INSET)).coerceAtLeast(headLength)
        val shaftEndX = startX + ux * shaftLength
        val shaftEndZ = startZ + uz * shaftLength

        val result = ArrayList<ArrowSegment>(3)
        result += ArrowSegment(
            centerX = (startX + shaftEndX) / 2f,
            centerZ = (startZ + shaftEndZ) / 2f,
            length = shaftLength,
            yawDegrees = yawOf(ux, uz),
        )

        val backAngle = atan2(-uz.toDouble(), -ux.toDouble())
        listOf(HEAD_SPREAD_DEGREES, -HEAD_SPREAD_DEGREES).forEach { spread ->
            val angle = backAngle + spread * DEG_TO_RAD
            val bx = cos(angle).toFloat()
            val bz = sin(angle).toFloat()
            result += ArrowSegment(
                centerX = shaftEndX + bx * headLength / 2f,
                centerZ = shaftEndZ + bz * headLength / 2f,
                length = headLength,
                yawDegrees = yawOf(bx, bz),
            )
        }

        return result
    }

    /** Yaw that turns a bar's local +X axis onto the ground direction (x, z). */
    fun yawOf(x: Float, z: Float): Float = (atan2(-z.toDouble(), x.toDouble()) * RAD_TO_DEG).toFloat()
}
