package com.nefeon.akzsa.design

import kotlin.math.pow

/**
 * AKZSA Midnight design tokens (single source of truth).
 *
 * Plain ARGB ints and plain Kotlin only, so the same tokens feed:
 *  - the Compose theme (`Color(palette.background)`),
 *  - the classic-View keyboard (`view.setBackgroundColor(palette.background)`),
 *  - res/values/colors.xml (kept in sync by GraphiteSignalTest).
 *
 * The accent is a FILL colour, always paired with [Palette.onAccent].
 */
object GraphiteSignal {

    data class Palette(
        val background: Int,
        /** Keyboard body behind the keys (slightly different from the app background). */
        val keyboardBackground: Int,
        val surface: Int,
        val surfaceElevated: Int,
        val secondarySurface: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        /** Decorative hairlines only (not an accessibility boundary). */
        val border: Int,
        /** Boundaries of interactive controls; >= 3:1 against background and surface. */
        val borderStrong: Int,
        val accent: Int,
        val accentPressed: Int,
        val onAccent: Int,
        /** Voice / microphone accent (teal). */
        val voice: Int,
        val focus: Int,
        val error: Int,
        val onError: Int,
        val success: Int,
        val disabledContainer: Int,
        val disabledContent: Int,
        val secondarySurfacePressed: Int,
        val surfacePressed: Int,
    )

    // AKZSA Midnight system: a restrained neutral base with one calm indigo action colour and one teal voice accent.
    val Light = Palette(
        background = 0xFFF5F6F8.toInt(),
        keyboardBackground = 0xFFF0F1F4.toInt(),
        surface = 0xFFFFFFFF.toInt(),               // individual keys
        surfaceElevated = 0xFFFFFFFF.toInt(),
        secondarySurface = 0xFFE8ECF7.toInt(),      // selected surface
        textPrimary = 0xFF252A33.toInt(),
        textSecondary = 0xFF747C89.toInt(),
        border = 0xFFDFE3E9.toInt(),
        borderStrong = 0xFF6F7887.toInt(),
        accent = 0xFF5267C9.toInt(),                // main action
        accentPressed = 0xFF4156B5.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        voice = 0xFF28A6B8.toInt(),
        focus = 0xFF5267C9.toInt(),
        error = 0xFFB3261E.toInt(),
        onError = 0xFFFFFFFF.toInt(),
        success = 0xFF276B43.toInt(),
        disabledContainer = 0xFFE8EAEE.toInt(),
        disabledContent = 0xFF6F7683.toInt(),
        secondarySurfacePressed = 0xFFD5DBEE.toInt(),
        surfacePressed = 0xFFE3E6EC.toInt(),
    )

    val Dark = Palette(
        background = 0xFF202329.toInt(),
        keyboardBackground = 0xFF252A31.toInt(),
        surface = 0xFF323842.toInt(),
        surfaceElevated = 0xFF3A404B.toInt(),
        secondarySurface = 0xFF353E50.toInt(),
        textPrimary = 0xFFF2F4F7.toInt(),
        textSecondary = 0xFFA8B0BC.toInt(),
        border = 0xFF3D444E.toInt(),
        borderStrong = 0xFF8993A3.toInt(),
        accent = 0xFF7890ED.toInt(),
        accentPressed = 0xFF6C84E3.toInt(),
        onAccent = 0xFF14172A.toInt(),
        voice = 0xFF48B9C8.toInt(),
        focus = 0xFF7890ED.toInt(),
        error = 0xFFFFB4AB.toInt(),
        onError = 0xFF202329.toInt(),
        success = 0xFF7BD3A0.toInt(),
        disabledContainer = 0xFF2B3038.toInt(),
        disabledContent = 0xFF7E8693.toInt(),
        secondarySurfacePressed = 0xFF445068.toInt(),
        surfacePressed = 0xFF3F4652.toInt(),
    )

    fun palette(dark: Boolean): Palette = if (dark) Dark else Light

    /** Resolves `AppSettings.theme` ("light", "dark" or "system"; any old value falls back to system). */
    fun paletteForTheme(theme: String, systemDark: Boolean): Palette = when (theme) {
        "dark" -> Dark
        "light" -> Light
        else -> palette(systemDark)
    }

    /** Layout/shape/motion tokens in dp / sp / ms. Use these instead of literals. */
    object Dimens {
        const val minTouchTargetDp = 48
        const val spaceXs = 4
        const val spaceS = 8
        const val spaceM = 12
        const val spaceL = 16
        const val spaceXl = 24
        const val radiusKey = 8
        const val radiusControl = 12
        const val radiusCard = 16
        const val strokeHairline = 1
        const val strokeFocus = 2
        const val iconS = 18
        const val iconM = 24
        const val keyLabelSp = 18f
        const val toolbarLabelSp = 13f
        const val captionSp = 12f
        const val bodySp = 15f
        const val motionFastMs = 120
        const val motionNormalMs = 200
    }

    // ---- WCAG 2.x contrast (used by tests and by runtime guard rails) ----

    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun relativeLuminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    fun contrastRatio(foreground: Int, background: Int): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        val hi = maxOf(a, b)
        val lo = minOf(a, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Hex such as `#C8F169` (alpha dropped) for docs and XML generation. */
    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
}
