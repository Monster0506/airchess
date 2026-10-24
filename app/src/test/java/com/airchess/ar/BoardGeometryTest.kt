package com.airchess.ar

import com.airchess.chess.ChessSquare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoardGeometryTest {

    private val geometry = BoardGeometry()

    private fun assertSquareAt(name: String, x: Float, z: Float, geometry: BoardGeometry = this.geometry) {
        val position = geometry.squareToLocalPosition(ChessSquare.fromAlgebraic(name))
        assertEquals("$name x", x, position.x, EPS)
        assertEquals("$name y", 0f, position.y, EPS)
        assertEquals("$name z", z, position.z, EPS)
    }

    @Test
    fun squareSizeIsEighthOfBoard() {
        assertEquals(0.05f, geometry.squareSize, EPS)
    }

    @Test
    fun cornersMatchContract() {
        assertSquareAt("a1", -0.175f, 0.175f)
        assertSquareAt("h1", 0.175f, 0.175f)
        assertSquareAt("a8", -0.175f, -0.175f)
        assertSquareAt("h8", 0.175f, -0.175f)
    }

    @Test
    fun centerSquaresMatchContract() {
        assertSquareAt("d4", -0.025f, 0.025f)
        assertSquareAt("e4", 0.025f, 0.025f)
    }

    @Test
    fun roundTripForAllSquares() {
        ChessSquare.ALL.forEach { square ->
            val position = geometry.squareToLocalPosition(square)
            assertEquals(square, geometry.localPositionToSquare(position.x, position.z))
        }
    }

    @Test
    fun pointsInsideSquareMapToThatSquare() {
        val e4 = ChessSquare.fromAlgebraic("e4")
        val center = geometry.squareToLocalPosition(e4)
        val inset = geometry.squareSize * 0.49f

        listOf(-inset, inset).forEach { dx ->
            listOf(-inset, inset).forEach { dz ->
                assertEquals(e4, geometry.localPositionToSquare(center.x + dx, center.z + dz))
            }
        }
    }

    @Test
    fun outsideBoardIsNull() {
        assertNull(geometry.localPositionToSquare(0.21f, 0f))
        assertNull(geometry.localPositionToSquare(-0.21f, 0f))
        assertNull(geometry.localPositionToSquare(0f, 0.21f))
        assertNull(geometry.localPositionToSquare(0f, -0.21f))
        assertNull(geometry.localPositionToSquare(1f, 1f))
    }

    @Test
    fun boardEdgesBelongToBoard() {
        assertEquals(ChessSquare.A1, geometry.localPositionToSquare(-0.2f, 0.2f))
        assertEquals(ChessSquare.H8, geometry.localPositionToSquare(0.2f, -0.2f))
        assertNotNull(geometry.localPositionToSquare(0f, 0f))
    }

    @Test
    fun customBoardSizeScalesEverything() {
        val big = BoardGeometry(boardSize = 0.8f)

        assertEquals(0.1f, big.squareSize, EPS)
        assertSquareAt("a1", -0.35f, 0.35f, big)
        assertSquareAt("h8", 0.35f, -0.35f, big)
        assertEquals(ChessSquare.fromAlgebraic("e4"), big.localPositionToSquare(0.05f, 0.05f))
        assertNull(big.localPositionToSquare(0.41f, 0f))
    }

    private companion object {
        const val EPS = 1e-5f
    }
}
