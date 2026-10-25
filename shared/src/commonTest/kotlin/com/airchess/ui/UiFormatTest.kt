package com.airchess.ui

import kotlin.test.assertEquals
import kotlin.test.Test

class UiFormatTest {

    @Test
    fun clockFormatting() {
        assertEquals("1:00", formatClock(60_000))
        assertEquals("4:05", formatClock(244_500))
        assertEquals("0:10", formatClock(10_000))
        assertEquals("0:09.4", formatClock(9_450))
        assertEquals("0:00.0", formatClock(-5))
    }

    @Test
    fun codeInputIsUppercasedFilteredAndTrimmed() {
        assertEquals("ABC234", sanitizeCode("abc-234zz"))
        assertEquals("AB", sanitizeCode("a0b1"))
    }
}
