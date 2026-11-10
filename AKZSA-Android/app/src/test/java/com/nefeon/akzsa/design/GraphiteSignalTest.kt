package com.nefeon.akzsa.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphiteSignalTest {
    private val textMin = 4.5    // WCAG AA, normal text
    private val nonTextMin = 3.0 // WCAG AA, UI boundaries / focus indicators

    private fun check(name: String, fg: Int, bg: Int, min: Double) {
        val ratio = GraphiteSignal.contrastRatio(fg, bg)
        assertTrue("$name contrast ${"%.2f".format(ratio)} < $min", ratio >= min)
    }

    /** [secondaryMin]: the specified light-theme secondary text (#747C89) is ~3.9:1, i.e. above the 3:1 UI-text floor but below AA 4.5. */
    private fun verify(label: String, p: GraphiteSignal.Palette, secondaryMin: Double = textMin) {
        val containers = mapOf(
            "background" to p.background, "surface" to p.surface,
            "surfaceElevated" to p.surfaceElevated, "secondarySurface" to p.secondarySurface,
            "keyboardBackground" to p.keyboardBackground,
        )
        for ((cn, c) in containers) {
            check("$label textPrimary/$cn", p.textPrimary, c, textMin)
            check("$label textSecondary/$cn", p.textSecondary, c, secondaryMin)
            check("$label error/$cn", p.error, c, textMin)
            check("$label success/$cn", p.success, c, textMin)
            check("$label focus/$cn", p.focus, c, nonTextMin)
            check("$label borderStrong/$cn", p.borderStrong, c, nonTextMin)
        }
        // Accent is a fill: its label colour must be readable in normal and pressed states.
        check("$label onAccent/accent", p.onAccent, p.accent, textMin)
        check("$label onAccent/accentPressed", p.onAccent, p.accentPressed, textMin)
        check("$label onError/error", p.onError, p.error, textMin)
        // Disabled content is exempt from WCAG but must stay perceivable.
        check("$label disabledContent/disabledContainer", p.disabledContent, p.disabledContainer, nonTextMin)
    }

    @Test fun lightPaletteMeetsContrast() = verify("light", GraphiteSignal.Light, secondaryMin = 3.0)
    @Test fun darkPaletteMeetsContrast() = verify("dark", GraphiteSignal.Dark)

    @Test fun specifiedLightValuesAreUsed() {
        val l = GraphiteSignal.Light
        assertEquals("#F5F6F8", GraphiteSignal.hex(l.background))
        assertEquals("#F0F1F4", GraphiteSignal.hex(l.keyboardBackground))
        assertEquals("#FFFFFF", GraphiteSignal.hex(l.surface))
        assertEquals("#252A33", GraphiteSignal.hex(l.textPrimary))
        assertEquals("#747C89", GraphiteSignal.hex(l.textSecondary))
        assertEquals("#DFE3E9", GraphiteSignal.hex(l.border))
        assertEquals("#E8ECF7", GraphiteSignal.hex(l.secondarySurface))
        assertEquals("#5267C9", GraphiteSignal.hex(l.accent))
        assertEquals("#28A6B8", GraphiteSignal.hex(l.voice))
    }

    @Test fun specifiedDarkValuesAreUsed() {
        val d = GraphiteSignal.Dark
        assertEquals("#202329", GraphiteSignal.hex(d.background))
        assertEquals("#252A31", GraphiteSignal.hex(d.keyboardBackground))
        assertEquals("#323842", GraphiteSignal.hex(d.surface))
        assertEquals("#F2F4F7", GraphiteSignal.hex(d.textPrimary))
        assertEquals("#A8B0BC", GraphiteSignal.hex(d.textSecondary))
        assertEquals("#3D444E", GraphiteSignal.hex(d.border))
        assertEquals("#353E50", GraphiteSignal.hex(d.secondarySurface))
        assertEquals("#7890ED", GraphiteSignal.hex(d.accent))
        assertEquals("#48B9C8", GraphiteSignal.hex(d.voice))
    }

    @Test fun onlyLightAndDarkExistAndOldThemeKeysFallBackToSystem() {
        assertEquals(GraphiteSignal.Light, GraphiteSignal.paletteForTheme("light", true))
        assertEquals(GraphiteSignal.Dark, GraphiteSignal.paletteForTheme("dark", false))
        assertEquals(GraphiteSignal.Dark, GraphiteSignal.paletteForTheme("system", true))
        assertEquals(GraphiteSignal.Light, GraphiteSignal.paletteForTheme("midnight_ivory", false))
    }

    @Test fun contrastMathMatchesKnownValues() {
        assertEquals(21.0, GraphiteSignal.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, GraphiteSignal.contrastRatio(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
    }

    @Test fun xmlColorsStayInSyncWithTokens() {
        val xml = File("src/main/res/values/colors.xml").takeIf { it.isFile }
            ?: File("app/src/main/res/values/colors.xml")
        assertTrue("colors.xml not found from ${File(".").absolutePath}", xml.isFile)
        val text = xml.readText()
        fun expectColor(name: String, argb: Int) {
            val m = Regex("<color name=\"$name\">(#[0-9A-Fa-f]{6,8})</color>").find(text)
            assertTrue("colors.xml missing $name", m != null)
            assertEquals("colors.xml $name out of sync", GraphiteSignal.hex(argb), "#" + m!!.groupValues[1].takeLast(6).uppercase())
        }
        val l = GraphiteSignal.Light; val d = GraphiteSignal.Dark
        expectColor("gs_light_background", l.background); expectColor("gs_light_surface", l.surface)
        expectColor("gs_light_secondary_surface", l.secondarySurface); expectColor("gs_light_text_primary", l.textPrimary)
        expectColor("gs_light_text_secondary", l.textSecondary); expectColor("gs_light_accent", l.accent)
        expectColor("gs_light_on_accent", l.onAccent); expectColor("gs_light_focus", l.focus)
        expectColor("gs_light_border", l.border); expectColor("gs_light_keyboard", l.keyboardBackground); expectColor("gs_light_voice", l.voice)
        expectColor("gs_dark_background", d.background); expectColor("gs_dark_surface", d.surface)
        expectColor("gs_dark_secondary_surface", d.secondarySurface); expectColor("gs_dark_text_primary", d.textPrimary)
        expectColor("gs_dark_text_secondary", d.textSecondary); expectColor("gs_dark_accent", d.accent)
        expectColor("gs_dark_on_accent", d.onAccent); expectColor("gs_dark_focus", d.focus)
        expectColor("gs_dark_border", d.border); expectColor("gs_dark_keyboard", d.keyboardBackground); expectColor("gs_dark_voice", d.voice)
    }
}
