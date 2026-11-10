package com.nefeon.akzsa.keyboard

import kotlin.math.max

/**
 * Pure sizing rules for the seven-button toolbar (no Android types, so they are unit-tested).
 *
 * Every toolbar item has the same structure: a fixed icon box, a fixed gap, one label line. The only decision
 * left is the width of a cell: when all items fit at a comfortable touch size they share the row equally,
 * otherwise cells keep a readable minimum width and the row scrolls with the last visible item cut halfway
 * (the "peek") so it is obvious that more actions exist.
 */
internal object ToolbarLayoutPolicy {
    /** Smallest comfortable touch width (and the toolbar height, see [KeyboardLayoutPolicy]). */
    const val MIN_TOUCH_DP = 48f
    /** Horizontal breathing room a label needs inside its cell. */
    const val LABEL_PAD_DP = 4f
    const val MIN_LABEL_SP = 10f
    const val MAX_LABEL_SP = 11f
    /** Drawn glyph size; the icon box is larger so the glyph never touches the label or the cell edge. */
    const val ICON_VISUAL_DP = 24f
    const val ICON_BOX_DP = 28f
    const val ICON_LABEL_GAP_DP = 4f
    /** Items visible at once in scroll mode: the last one is cut in half as a scroll hint. */
    const val PEEK_VISIBLE_ITEMS = 6.5f

    data class Layout(val cellWidthDp: Float, val scrolls: Boolean, val visibleItems: Float)

    /** Narrowest cell that still gives a 48dp touch target and shows the widest label without clipping. */
    fun requiredCellDp(widestLabelDp: Float): Float = max(MIN_TOUCH_DP, widestLabelDp + LABEL_PAD_DP)

    fun compute(availableWidthDp: Float, itemCount: Int, widestLabelDp: Float): Layout {
        val count = max(1, itemCount)
        val available = if (availableWidthDp.isNaN()) 0f else max(0f, availableWidthDp)
        val minCell = requiredCellDp(widestLabelDp)
        val equal = available / count
        if (equal >= minCell) return Layout(equal, scrolls = false, visibleItems = count.toFloat())
        val cell = max(minCell, available / PEEK_VISIBLE_ITEMS.coerceAtMost(count - 0.5f))
        return Layout(cell, scrolls = true, visibleItems = if (cell > 0f) available / cell else 0f)
    }

    /** Largest label size in [MIN_LABEL_SP]..[MAX_LABEL_SP] whose widest label fits [cellWidthDp]; one size for all items. */
    fun pickLabelSp(cellWidthDp: Float, widestLabelDpAt: (Float) -> Float): Float {
        var sp = MAX_LABEL_SP
        while (sp > MIN_LABEL_SP && widestLabelDpAt(sp) + LABEL_PAD_DP > cellWidthDp) sp -= 0.5f
        return max(MIN_LABEL_SP, sp)
    }
}
