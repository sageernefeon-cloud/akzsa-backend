package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarLayoutPolicyTest {
    private val items = 7
    /** Roughly the width of "Clipboard" at 10sp in dp. */
    private val widest = 46f

    private fun availableDp(widthDp: Int, hPadDp: Int) = (widthDp - 2 * hPadDp).toFloat()

    @Test fun fixedMinimumOf54dpIsGone_360dpPhoneFitsAllSevenInOneRow() {
        val l = ToolbarLayoutPolicy.compute(availableDp(360, 6), items, widest)
        assertFalse(l.scrolls)
        assertTrue("cell ${l.cellWidthDp}", l.cellWidthDp >= ToolbarLayoutPolicy.MIN_TOUCH_DP)
        assertEquals(availableDp(360, 6), l.cellWidthDp * items, 0.01f)   // exactly fills, never overflows
    }

    @Test fun narrow320dpScrollsWithPeekAndKeepsTouchTarget() {
        val l = ToolbarLayoutPolicy.compute(availableDp(320, 6), items, widest)
        assertTrue(l.scrolls)
        assertTrue(l.cellWidthDp >= ToolbarLayoutPolicy.MIN_TOUCH_DP)
        assertTrue("visible ${l.visibleItems}", l.visibleItems < items)      // something is hidden...
        assertTrue("visible ${l.visibleItems}", l.visibleItems % 1f > 0.1f)  // ...and the last item is cut: scroll hint
    }

    @Test fun commonPhoneAndTabletWidthsFit() {
        for ((w, pad) in listOf(393 to 6, 412 to 6, 600 to 12, 720 to 12, 640 to 6)) {
            val l = ToolbarLayoutPolicy.compute(availableDp(w, pad), items, widest)
            assertFalse("w=$w", l.scrolls)
            assertTrue("w=$w cell ${l.cellWidthDp}", l.cellWidthDp >= ToolbarLayoutPolicy.MIN_TOUCH_DP)
        }
    }

    @Test fun landscapeContentWidthIsBoundedAndFits() {
        val m = KeyboardLayoutPolicy.metrics(851f, 393f)
        val l = ToolbarLayoutPolicy.compute(availableDp(m.contentWidthDp, m.horizontalPaddingDp), items, widest)
        assertFalse(l.scrolls)
    }

    @Test fun neverShrinksBelowTouchTargetOrLabelWidth_acrossWidthsAndLabels() {
        for (w in 240..900 step 4) for (label in listOf(30f, 46f, 60f)) {
            val l = ToolbarLayoutPolicy.compute(availableDp(w, 6), items, label)
            assertTrue("w=$w cell ${l.cellWidthDp}", l.cellWidthDp >= ToolbarLayoutPolicy.MIN_TOUCH_DP)
            assertTrue("w=$w label=$label", l.cellWidthDp >= label + ToolbarLayoutPolicy.LABEL_PAD_DP - 0.001f)
            if (!l.scrolls) assertTrue(l.cellWidthDp * items <= availableDp(w, 6) + 0.01f)   // no overflow when not scrolling
        }
    }

    @Test fun largeFontScaleFallsBackToScrollingInsteadOfClipping() {
        val l = ToolbarLayoutPolicy.compute(availableDp(393, 6), items, 70f)
        assertTrue(l.scrolls)
        assertTrue(l.cellWidthDp >= 74f)
    }

    @Test fun degenerateInputsDoNotCrash() {
        assertTrue(ToolbarLayoutPolicy.compute(0f, items, widest).scrolls)
        assertTrue(ToolbarLayoutPolicy.compute(Float.NaN, items, widest).cellWidthDp >= ToolbarLayoutPolicy.MIN_TOUCH_DP)
        assertFalse(ToolbarLayoutPolicy.compute(100f, 1, widest).scrolls)
    }

    @Test fun labelSizeIsOneValueWithinTenToElevenSp() {
        val wide = ToolbarLayoutPolicy.pickLabelSp(80f) { sp -> sp * 4.4f }
        assertEquals(ToolbarLayoutPolicy.MAX_LABEL_SP, wide, 0f)
        val tight = ToolbarLayoutPolicy.pickLabelSp(50f) { sp -> sp * 4.4f }
        assertTrue(tight >= ToolbarLayoutPolicy.MIN_LABEL_SP && tight <= ToolbarLayoutPolicy.MAX_LABEL_SP)
        assertEquals(ToolbarLayoutPolicy.MIN_LABEL_SP, ToolbarLayoutPolicy.pickLabelSp(10f) { sp -> sp * 4.4f }, 0f)
    }

    @Test fun iconBoxLabelAndGapFitInsideEveryToolbarHeight() {
        val content = ToolbarLayoutPolicy.ICON_BOX_DP + ToolbarLayoutPolicy.ICON_LABEL_GAP_DP + 1.25f * ToolbarLayoutPolicy.MAX_LABEL_SP
        for (w in listOf(320f, 360f, 393f, 412f, 600f, 851f)) for (h in listOf(393f, 640f, 851f)) {
            val m = KeyboardLayoutPolicy.metrics(w, h)
            assertTrue("toolbar ${m.toolbarHeightDp} for ${w}x$h", m.toolbarHeightDp >= ToolbarLayoutPolicy.MIN_TOUCH_DP.toInt())
            assertTrue("content $content in ${m.toolbarHeightDp}", content <= m.toolbarHeightDp)
        }
        assertTrue(ToolbarLayoutPolicy.ICON_VISUAL_DP < ToolbarLayoutPolicy.ICON_BOX_DP)
    }

    @Test fun toolbarGlyphsAreOpticallyCentred() {
        val toolbarIcons = listOf(KeyIcon.SPARKLE, KeyIcon.CLIPBOARD, KeyIcon.EMOJI, KeyIcon.GIF, KeyIcon.SNIPPETS, KeyIcon.LANGUAGE, KeyIcon.SETTINGS)
        for (icon in toolbarIcons) {
            val (dx, dy) = KeyIcons.opticalOffset(icon)
            assertTrue("$icon dx=$dx", kotlin.math.abs(dx) <= 0.6f)   // offsets are small corrections, not re-layouts
            assertTrue("$icon dy=$dy", kotlin.math.abs(dy) <= 0.6f)
        }
        assertEquals(-0.5f, KeyIcons.opticalOffset(KeyIcon.SNIPPETS).first, 0.001f)   // the document glyph sat half a unit right
    }
}
