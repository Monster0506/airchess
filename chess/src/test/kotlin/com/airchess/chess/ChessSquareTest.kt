package com.airchess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessSquareTest {

    @Test
    fun cornersAndCenter() {
        assertEquals(0, sq("a1").index)
        assertEquals(63, sq("h8").index)
        assertEquals(28, sq("e4").index)
        assertEquals("e4", ChessSquare.of(28).algebraic)
    }

    @Test
    fun roundTripAllSquares() {
        for (square in ChessSquare.ALL) {
            assertEquals(square, ChessSquare.fromAlgebraic(square.algebraic))
            assertEquals(square, ChessSquare.at(square.file, square.rank))
        }
    }

    @Test
    fun invalidInputs() {
        assertNull(ChessSquare.parse("i1"))
        assertNull(ChessSquare.parse("a9"))
        assertNull(ChessSquare.parse("e"))
        assertNull(sq("a1").offset(-1, 0))
        assertTrue(sq("a1").offset(1, 1) == sq("b2"))
    }

    @Test
    fun lightSquares() {
        assertFalse(sq("a1").isLight)
        assertTrue(sq("h1").isLight)
    }
}
