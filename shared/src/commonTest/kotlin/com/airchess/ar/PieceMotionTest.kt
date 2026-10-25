package com.airchess.ar

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class PieceMotionTest {

    @Test
    fun progressIsClampedToUnitRange() {
        assertEquals(0f, PieceMotion.progress(-5L), 0f)
        assertEquals(0.5f, PieceMotion.progress(PieceMotion.DURATION_NANOS / 2), 1e-5f)
        assertEquals(1f, PieceMotion.progress(PieceMotion.DURATION_NANOS * 3), 0f)
    }

    @Test
    fun easingHasFixedEndpointsAndIsSlowAtTheEnds() {
        assertEquals(0f, PieceMotion.easeInOut(0f), 1e-6f)
        assertEquals(1f, PieceMotion.easeInOut(1f), 1e-6f)
        assertEquals(0.5f, PieceMotion.easeInOut(0.5f), 1e-6f)
        assertTrue(PieceMotion.easeInOut(0.1f) < 0.1f)
        assertTrue(PieceMotion.easeInOut(0.9f) > 0.9f)
    }

    @Test
    fun arcPeaksInTheMiddleAndLandsOnTheBoard() {
        assertEquals(0f, PieceMotion.arc(0f, 0.03f), 1e-6f)
        assertEquals(0.03f, PieceMotion.arc(0.5f, 0.03f), 1e-6f)
        assertEquals(0f, PieceMotion.arc(1f, 0.03f), 1e-5f)
    }
}
