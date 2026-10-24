package com.airchess.ar

import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Transform
import io.github.sceneview.math.times
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowLayoutTest {

    private val geometry = BoardGeometry()

    private fun axisAfterYaw(yaw: Float): Float3 =
        Transform(Position(0f, 0f, 0f), Rotation(0f, yaw, 0f)) * Float3(1f, 0f, 0f)

    @Test
    fun yawTurnsLocalXOntoGroundDirection() {
        listOf(0f to 1f, 1f to 0f, 0f to -1f, -1f to 0f, 0.6f to -0.8f).forEach { (x, z) ->
            val axis = axisAfterYaw(ArrowLayout.yawOf(x, z))

            assertEquals("x for ($x,$z)", x, axis.x, 1e-4f)
            assertEquals("z for ($x,$z)", z, axis.z, 1e-4f)
        }
    }

    @Test
    fun arrowHasShaftAndTwoHeadBars() {
        val segments = ArrowLayout.segments(
            geometry.squareToLocalPosition(com.airchess.chess.ChessSquare.fromAlgebraic("e2")),
            geometry.squareToLocalPosition(com.airchess.chess.ChessSquare.fromAlgebraic("e4")),
            geometry.squareSize,
        )

        assertEquals(3, segments.size)
    }

    @Test
    fun shaftLiesBetweenTheSquaresAndHeadSitsAtTheTip() {
        val e2 = geometry.squareToLocalPosition(com.airchess.chess.ChessSquare.fromAlgebraic("e2"))
        val e4 = geometry.squareToLocalPosition(com.airchess.chess.ChessSquare.fromAlgebraic("e4"))

        val (shaft, headA, headB) = ArrowLayout.segments(e2, e4, geometry.squareSize)

        assertEquals(e2.x, shaft.centerX, 1e-5f)
        assertTrue(shaft.centerZ < e2.z && shaft.centerZ > e4.z)
        assertTrue(shaft.length < 2f * geometry.squareSize)
        assertTrue(headA.centerZ > e4.z && headB.centerZ > e4.z)
        assertTrue(headA.centerX != headB.centerX)
    }

    @Test
    fun sameSquareGivesNoArrow() {
        val c = geometry.squareToLocalPosition(com.airchess.chess.ChessSquare.A1)

        assertTrue(ArrowLayout.segments(c, c, geometry.squareSize).isEmpty())
    }
}
