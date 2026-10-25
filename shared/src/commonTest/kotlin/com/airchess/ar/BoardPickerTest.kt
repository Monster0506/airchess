package com.airchess.ar

import com.airchess.chess.ChessPosition
import com.airchess.chess.ChessSquare
import com.airchess.chess.PieceType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class BoardPickerTest {

    private val geometry = BoardGeometry()

    private fun sq(name: String) = ChessSquare.fromAlgebraic(name)

    private fun centerOf(name: String) = geometry.squareToLocalPosition(sq(name))

    @Test
    fun straightDownRayPicksSquareUnderIt() {
        val c = centerOf("e4")

        val hit = BoardPicker.pick(geometry, Vec3(c.x, 0.5f, c.z), Vec3(0f, -1f, 0f), emptyMap())

        assertEquals(sq("e4"), hit)
    }

    @Test
    fun rayMissingTheBoardPicksNothing() {
        assertNull(BoardPicker.pick(geometry, Vec3(0.5f, 0.5f, 0f), Vec3(0f, -1f, 0f), emptyMap()))
    }

    @Test
    fun rayPointingUpPicksNothing() {
        assertNull(BoardPicker.pick(geometry, Vec3(0f, 0.5f, 0f), Vec3(0f, 1f, 0f), emptyMap()))
    }

    @Test
    fun obliqueRayThroughPieceBodyPicksThePieceSquare() {
        val piece = centerOf("e2")
        val origin = Vec3(piece.x, 0.3f, piece.z + 0.4f)
        val target = Vec3(piece.x, 0.03f, piece.z)
        val direction = Vec3(target.x - origin.x, target.y - origin.y, target.z - origin.z)

        val withPiece = BoardPicker.pick(geometry, origin, direction, mapOf(sq("e2") to PieceType.BISHOP))
        val withoutPiece = BoardPicker.pick(geometry, origin, direction, emptyMap())

        assertEquals(sq("e2"), withPiece)
        assertEquals(sq("e3"), withoutPiece)
    }

    @Test
    fun nearestPieceWinsOverFartherOne() {
        val near = centerOf("e2")
        val origin = Vec3(near.x, 0.04f, near.z + 0.5f)

        val hit = BoardPicker.pick(geometry, origin, Vec3(0f, 0f, -1f), mapOf(sq("e2") to PieceType.QUEEN, sq("e5") to PieceType.QUEEN, sq("e7") to PieceType.QUEEN))

        assertEquals(sq("e2"), hit)
    }

    @Test
    fun topOfPieceIsHittable() {
        val c = centerOf("c3")

        val hit = BoardPicker.pick(geometry, Vec3(c.x, 0.5f, c.z), Vec3(0f, -1f, 0f), mapOf(sq("c3") to PieceType.KING))

        assertEquals(sq("c3"), hit)
    }

    @Test
    fun tapOnPawnJustAboveKingTopPicksThePawn() {
        val pieces = ChessPosition.INITIAL.pieces().associate { (square, piece) -> square to piece.type }
        val origin = Vec3(2.4064156E-4f, 0.79234356f, 0.7935144f)
        val direction = Vec3(23.985607f, -763.15356f, -646.4475f)

        val hit = BoardPicker.pick(geometry, origin, direction, pieces)

        assertEquals(sq("e2"), hit)
    }

    @Test
    fun blackPerspectiveRotatedRootStillMapsToTheTappedSquare() {
        ChessSquare.ALL.forEach { square ->
            val local = geometry.squareToLocalPosition(square)
            val world = rotatedHalfTurn(local)

            val hit = BoardPicker.pick(
                geometry,
                rotatedHalfTurn(Vec3(world.x, 0.5f, world.z)),
                Vec3(0f, -1f, 0f),
                emptyMap(),
            )

            assertEquals(square, hit)
        }
    }

    @Test
    fun blackPerspectivePutsA1AtFarRightFromTheWhiteSide() {
        val world = rotatedHalfTurn(geometry.squareToLocalPosition(ChessSquare.A1))

        assertEquals(0.175f, world.x, 1e-4f)
        assertEquals(-0.175f, world.z, 1e-4f)
    }

    /** The black perspective turns the content root 180 degrees about +Y. */
    private fun rotatedHalfTurn(v: Vec3) = Vec3(-v.x, v.y, -v.z)
}
