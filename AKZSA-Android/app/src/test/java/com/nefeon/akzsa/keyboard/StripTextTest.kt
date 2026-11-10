package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StripTextTest {
    @Test fun emptyAndBlankShowNothing() {
        assertEquals("", StripText.compose(null))
        assertEquals("", StripText.compose("  \n "))
    }

    @Test fun shortTextIsShownWithWhitespaceCollapsed() {
        assertEquals("hey man how are you", StripText.compose("hey  man\nhow are you"))
    }

    @Test fun longTextKeepsOnlyTheTailWithAnEllipsis() {
        val shown = StripText.compose("word ".repeat(40))
        assertTrue(shown.startsWith("…"))
        assertTrue(shown.length <= StripText.MAX + 1)
        assertTrue(shown.endsWith("word"))
    }
}
