package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyTouchPolicyTest {
    @Test fun insideKeyCounts() {
        assertTrue(KeyTouchPolicy.isInside(10f, 10f, 100, 50, 0f))
        assertTrue(KeyTouchPolicy.isInside(0f, 0f, 100, 50, 0f))
        assertTrue(KeyTouchPolicy.isInside(100f, 50f, 100, 50, 0f))
    }

    @Test fun slidingOffCancels() {
        assertFalse(KeyTouchPolicy.isInside(-20f, 10f, 100, 50, 8f))
        assertFalse(KeyTouchPolicy.isInside(50f, 80f, 100, 50, 8f))
    }

    @Test fun slopForgivesSmallDrift() {
        assertTrue(KeyTouchPolicy.isInside(-5f, 10f, 100, 50, 8f))
        assertTrue(KeyTouchPolicy.isInside(104f, 54f, 100, 50, 8f))
    }

    @Test fun accentIndexIsClampedToPopup() {
        assertEquals(0, KeyTouchPolicy.accentIndexAt(-500f, 100f, 40f, 5))
        assertEquals(4, KeyTouchPolicy.accentIndexAt(5000f, 100f, 40f, 5))
        assertEquals(2, KeyTouchPolicy.accentIndexAt(190f, 100f, 40f, 5))
    }

    @Test fun accentIndexHandlesEmptyOrBadInput() {
        assertEquals(-1, KeyTouchPolicy.accentIndexAt(10f, 0f, 40f, 0))
        assertEquals(-1, KeyTouchPolicy.accentIndexAt(10f, 0f, 0f, 3))
    }

    @Test fun previewOnlyForSingleCharacters() {
        assertTrue(KeyTouchPolicy.wantsPreview("a"))
        assertTrue(KeyTouchPolicy.wantsPreview("क"))
        assertTrue(KeyTouchPolicy.wantsPreview("."))
        for (label in listOf("", "space", "?123", " ", "⌫", "⇧", "⇪", "⌨")) assertFalse(label, KeyTouchPolicy.wantsPreview(label))
    }
}
