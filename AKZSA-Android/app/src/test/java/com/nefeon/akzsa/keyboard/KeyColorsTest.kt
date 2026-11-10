package com.nefeon.akzsa.keyboard

import com.nefeon.akzsa.design.GraphiteSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyColorsTest {
    private val palettes = listOf(GraphiteSignal.Light, GraphiteSignal.Dark)

    @Test fun fillFollowsStyleAndPressedState() {
        for (p in palettes) {
            assertEquals(p.surface, KeyColors.fill(KeyStyle.NORMAL, p, false))
            assertEquals(p.surfacePressed, KeyColors.fill(KeyStyle.NORMAL, p, true))
            assertEquals(p.secondarySurface, KeyColors.fill(KeyStyle.SPECIAL, p, false))
            assertEquals(p.secondarySurfacePressed, KeyColors.fill(KeyStyle.SPECIAL, p, true))
            assertEquals(p.accent, KeyColors.fill(KeyStyle.ACCENT, p, false))
            assertEquals(p.accentPressed, KeyColors.fill(KeyStyle.ACCENT, p, true))
        }
    }

    @Test fun pressedFillIsVisiblyDifferent() {
        for (p in palettes) for (s in KeyStyle.values()) {
            assertNotEquals("$s pressed == resting", KeyColors.fill(s, p, false), KeyColors.fill(s, p, true))
        }
    }

    @Test fun accentKeysAreSolidWithOnAccentLabelAndMicHasVoiceRing() {
        for (p in palettes) {
            assertEquals(p.accent, KeyColors.border(KeyStyle.ACCENT, p))
            assertEquals(0, KeyColors.borderWidthDp(KeyStyle.ACCENT))
            assertEquals(p.onAccent, KeyColors.label(KeyStyle.ACCENT, p))
            assertEquals(p.onAccent, KeyColors.icon(KeyStyle.ACCENT, p))
            assertEquals(p.border, KeyColors.border(KeyStyle.NORMAL, p))
            assertEquals(1, KeyColors.borderWidthDp(KeyStyle.SPECIAL))
            assertEquals(p.textPrimary, KeyColors.label(KeyStyle.NORMAL, p))
            // The microphone: surface key, 2 dp teal ring, teal glyph.
            assertEquals(p.surface, KeyColors.fill(KeyStyle.VOICE, p, false))
            assertEquals(p.voice, KeyColors.border(KeyStyle.VOICE, p))
            assertEquals(2, KeyColors.borderWidthDp(KeyStyle.VOICE))
            assertEquals(p.voice, KeyColors.icon(KeyStyle.VOICE, p))
        }
    }

    @Test fun toolbarButtonsAreFlatAndSelectedOnesUseSelectedSurface() {
        for (p in palettes) {
            assertEquals(p.keyboardBackground, KeyColors.fill(KeyStyle.TOOL, p, false))
            assertEquals(p.secondarySurface, KeyColors.fill(KeyStyle.TOOL_ON, p, false))
            assertEquals(0, KeyColors.borderWidthDp(KeyStyle.TOOL))
            assertEquals(p.accent, KeyColors.icon(KeyStyle.TOOL_ON, p))
        }
    }

    @Test fun voiceGlyphIsClearlyVisibleOnItsKey() {
        // The specified teal is ~2.9:1 on white keys; the 2 dp ring and larger glyph carry it. Never go below 2.5.
        for (p in palettes) {
            val ratio = GraphiteSignal.contrastRatio(p.voice, KeyColors.fill(KeyStyle.VOICE, p, false))
            assertTrue("voice glyph contrast $ratio", ratio >= 2.5)
        }
    }

    @Test fun labelsStayReadableOnEveryKeyFill() {
        for (p in palettes) for (s in KeyStyle.values()) for (pressed in listOf(false, true)) {
            val ratio = GraphiteSignal.contrastRatio(KeyColors.label(s, p), KeyColors.fill(s, p, pressed))
            assertTrue("$s pressed=$pressed contrast $ratio", ratio >= 4.5)
        }
    }
}
