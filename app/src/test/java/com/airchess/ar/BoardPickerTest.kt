package com.airchess.ar

import com.airchess.chess.ChessSquare
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.inverse
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Transform
import io.github.sceneview.math.worldToLocalDirection
import io.github.sceneview.math.worldToLocalPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardPickerTest {

    private val geometry = BoardGeometry()

    private fun sq(name: String) = ChessSquare.fromAlgebraic(name)

    private fun centerOf(name: String) = geometry.squareToLocalPosition(sq(name))

    @Test
    fun straightDownRayPicksSquareUnderIt() {
        val c = centerOf("e4")

        val hit = BoardPicker.pick(geometry, Float3(c.x, 0.5f, c.z), Float3(0f, -1f, 0f), emptyList())

        assertEquals(sq("e4"), hit)
    }

    @Test
    fun rayMissingTheBoardPicksNothing() {
        assertNull(BoardPicker.pick(geometry, Float3(0.5f, 0.5f, 0f), Float3(0f, -1f, 0f), emptyList()))
    }

    @Test
    fun rayPointingUpPicksNothing() {
        assertNull(BoardPicker.pick(geometry, Float3(0f, 0.5f, 0f), Float3(0f, 1f, 0f), emptyList()))
    }

    @Test
    fun obliqueRayThroughPieceBodyPicksThePieceSquare() {
        val piece = centerOf("e2")
        val origin = Float3(piece.x, 0.3f, piece.z + 0.4f)
        val target = Float3(piece.x, 0.03f, piece.z)
        val direction = Float3(target.x - origin.x, target.y - origin.y, target.z - origin.z)

        val withPiece = BoardPicker.pick(geometry, origin, direction, listOf(sq("e2")))
        val withoutPiece = BoardPicker.pick(geometry, origin, direction, emptyList())

        assertEquals(sq("e2"), withPiece)
        assertEquals(sq("e3"), withoutPiece)
    }

    @Test
    fun nearestPieceWinsOverFartherOne() {
        val near = centerOf("e2")
        val origin = Float3(near.x, 0.04f, near.z + 0.5f)

        val hit = BoardPicker.pick(geometry, origin, Float3(0f, 0f, -1f), listOf(sq("e2"), sq("e5"), sq("e7")))

        assertEquals(sq("e2"), hit)
    }

    @Test
    fun topOfPieceIsHittable() {
        val c = centerOf("c3")

        val hit = BoardPicker.pick(geometry, Float3(c.x, 0.5f, c.z), Float3(0f, -1f, 0f), listOf(sq("c3")))

        assertEquals(sq("c3"), hit)
    }

    @Test
    fun blackPerspectiveRotatedRootStillMapsToTheTappedSquare() {
        val root = Transform(Position(0f, 0f, 0f), Rotation(0f, 180f, 0f))
        val toLocal = inverse(root)

        ChessSquare.ALL.forEach { square ->
            val local = geometry.squareToLocalPosition(square)
            val world = root * Float4(local, 1f)
            val worldOrigin = Float3(world.x, 0.5f, world.z)
            val worldDirection = Float3(0f, -1f, 0f)

            val hit = BoardPicker.pick(
                geometry,
                worldToLocalPosition(worldOrigin, toLocal),
                worldToLocalDirection(worldDirection, toLocal),
                emptyList(),
            )

            assertEquals(square, hit)
        }
    }

    @Test
    fun blackPerspectivePutsA1AtFarRightFromTheWhiteSide() {
        val root = Transform(Position(0f, 0f, 0f), Rotation(0f, 180f, 0f))
        val a1 = geometry.squareToLocalPosition(ChessSquare.A1)

        val world = root * Float4(a1, 1f)

        assertEquals(0.175f, world.x, 1e-4f)
        assertEquals(-0.175f, world.z, 1e-4f)
    }
}
