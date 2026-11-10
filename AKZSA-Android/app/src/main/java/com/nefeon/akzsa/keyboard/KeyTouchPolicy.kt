package com.nefeon.akzsa.keyboard

/**
 * Pure rules for how a finger interacts with a key, kept free of Android types so they are unit-tested.
 *
 * - Plain character keys type on touch-down (like Gboard), so they feel instant.
 * - Every other key (Shift, Enter, mode switches, accent keys) fires on release, and only if the finger is still
 *   on the key (within [SLOP_DP]); sliding off cancels it, so a drifting thumb never triggers the wrong key.
 */
internal object KeyTouchPolicy {
    /** How far outside the key a finger may drift and still count as "on" it. */
    const val SLOP_DP = 6f
    /** Hold time before the accent popup opens. */
    const val LONG_PRESS_MS = 320L

    private const val NON_CHARACTER_GLYPHS = "⌫⇧⇪⌨"

    /** [x]/[y] are relative to the key view's top-left. */
    fun isInside(x: Float, y: Float, width: Int, height: Int, slopPx: Float): Boolean =
        x >= -slopPx && y >= -slopPx && x <= width + slopPx && y <= height + slopPx

    /** Index of the accent cell under the finger, clamped to the popup (so sliding past either end still selects). */
    fun accentIndexAt(rawX: Float, popupLeft: Float, cellWidth: Float, count: Int): Int {
        if (count <= 0 || cellWidth <= 0f) return -1
        return ((rawX - popupLeft) / cellWidth).toInt().coerceIn(0, count - 1)
    }

    /** Whether pressing a key with this label should show the enlarged character preview. */
    fun wantsPreview(label: String): Boolean {
        if (label.isEmpty() || label.codePointCount(0, label.length) != 1) return false
        val c = label[0]
        return !c.isWhitespace() && NON_CHARACTER_GLYPHS.indexOf(c) < 0
    }
}
