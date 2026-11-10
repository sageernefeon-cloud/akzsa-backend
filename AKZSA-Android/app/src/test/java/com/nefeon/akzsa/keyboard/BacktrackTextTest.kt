package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class BacktrackTextTest {
    @Test fun keepsTheTrailingSpace() = assertEquals("Meet at 6pm ", BacktrackText.carryEdgeFormatting("Meet at 5pm ", "Meet at 6pm"))
    @Test fun keepsTheLeadingSpace() = assertEquals(" 6pm", BacktrackText.carryEdgeFormatting(" 5pm", "6pm"))
    @Test fun restoresTheFirstCapital() = assertEquals("Bangalore ", BacktrackText.carryEdgeFormatting("Chennai ", "bangalore"))
    @Test fun leavesLowercaseAloneWhenTheOriginalWasLowercase() = assertEquals("six pm", BacktrackText.carryEdgeFormatting("five pm", "six pm"))
    @Test fun blankReplacementIsReturnedUntouched() = assertEquals("  ", BacktrackText.carryEdgeFormatting("Hello ", "  "))
}
