package com.airchess.ar

import com.airchess.chess.ChessSquare
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class ArrowLayoutTest {

    private val geometry = BoardGeometry()

    /** Image of +X under a right-handed turn of [yaw] degrees about +Y. */
    private fun axisAfterYaw(yaw: Float): Vec3 {
        val radians = yaw * PI / 180.0
        return Vec3(cos(radians).toFloat(), 0f, -sin(radians).toFloat())
    }

    @Test
    fun yawTurnsLocalXOntoGroundDirection() {
        listOf(0f to 1f, 1f to 0f, 0f to -1f, -1f to 0f, 0.6f to -0.8f).forEach { (x, z) ->
            val axis = axisAfterYaw(ArrowLayout.yawOf(x, z))

            assertEquals(x, axis.x, 1e-4f, "x for ($x,$z)")
            assertEquals(z, axis.z, 1e-4f, "z for ($x,$z)")
        }
    }

    @Test
    fun arrowHasShaftAndTwoHeadBars() {
        val segments = ArrowLayout.segments(
            geometry.squareToLocalPosition(ChessSquare.fromAlgebraic("e2")),
            geometry.squareToLocalPosition(ChessSquare.fromAlgebraic("e4")),
            geometry.squareSize,
        )

        assertEquals(3, segments.size)
    }

    @Test
    fun shaftLiesBetweenTheSquaresAndHeadSitsAtTheTip() {
        val e2 = geometry.squareToLocalPosition(ChessSquare.fromAlgebraic("e2"))
        val e4 = geometry.squareToLocalPosition(ChessSquare.fromAlgebraic("e4"))

        val (shaft, headA, headB) = ArrowLayout.segments(e2, e4, geometry.squareSize)

        assertEquals(e2.x, shaft.centerX, 1e-5f)
        assertTrue(shaft.centerZ < e2.z && shaft.centerZ > e4.z)
        assertTrue(shaft.length < 2f * geometry.squareSize)
        assertTrue(headA.centerZ > e4.z && headB.centerZ > e4.z)
        assertTrue(headA.centerX != headB.centerX)
    }

    @Test
    fun sameSquareGivesNoArrow() {
        val c = geometry.squareToLocalPosition(ChessSquare.A1)

        assertTrue(ArrowLayout.segments(c, c, geometry.squareSize).isEmpty())
    }
}
