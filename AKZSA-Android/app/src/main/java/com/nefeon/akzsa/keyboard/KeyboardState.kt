package com.nefeon.akzsa.keyboard

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import com.nefeon.akzsa.commands.ActionPlan
import com.nefeon.akzsa.design.GraphiteSignal
import com.nefeon.akzsa.settings.AppSettings

/**
 * Mutable keyboard state shared by the controllers. Plain fields only: no views, no Android services.
 * (State that only one controller needs - recorder handles, File AI query text, backspace repeat - stays
 * private to that controller.)
 */
internal class KeyboardState {
    var settings = AppSettings()
    var status = IDLE_STATUS

    /** Adaptive sizing, recomputed by the renderer on every render / configuration change. */
    var kbMetrics: KeyboardMetrics = KeyboardLayoutPolicy.metrics(360f, 800f)
    var sensitivity = FieldSensitivity.NORMAL
    var passwordField = false

    // Typing layers
    var shiftEnabled = false
    var capsLock = false
    var lastShiftTapMs = 0L
    var symbolsMode = false
    var hindiLayout = false
    var hindiPage = 0
    /** Script code of the active native layout (see [NativeLayouts]); only meaningful while [hindiLayout] is true. */
    var nativeScript = "hi"
    // Emoji panel search
    var emojiSearching = false
    var symbolsPage = 0
    // Clipboard row: search text, category filter
    var clipSearching = false
    var clipQuery = ""
    var clipFilter = "All"
    var emojiQuery = ""

    // Voice session flags read by the renderer (mic key label)
    var listening = false
    var cloudUploadInProgress = false
    var pendingPlan: ActionPlan? = null

    /**
     * The exact text AKZSA itself last inserted as dictation (plain dictation or a snippet expansion),
     * for Backtrack ("actually, make that 6pm"). Cleared on new-field focus and by any other edit, so a
     * correction can never be applied against text the user typed or edited by hand.
     */
    var lastDictationText: String? = null

    // Toolbar / panels (at most one is open at a time)
    var emojiPanelOpen = false
    var cursorRowOpen = false
    var clipboardRowOpen = false
    var gifPanelOpen = false
    var snippetsPanelOpen = false
    var languagesPanelOpen = false

    /** The AI Workspace panel is open on [aiTab]. Grammar Fix and AI Emoji are separate tabs. */
    var aiPanelOpen = false
    var aiTab = AiTab.GRAMMAR

    /** Re-runs the AI tool whose result is waiting for review ("Regenerate"); null when there is nothing to re-run. */
    var regenerate: (() -> Unit)? = null

    /** Latest speech level 0..1 from the on-device recogniser, for the listening waveform. */
    @Volatile var audioLevel = 0f

    companion object {
        const val IDLE_STATUS = "One mic · speak naturally"
    }
}

/** Density and Graphite Signal token access for everything that builds views. */
internal class KeyboardUi(private val ime: InputMethodService, private val state: KeyboardState) {
    fun dp(value: Int): Int = (value * ime.resources.displayMetrics.density).toInt()

    private fun isNight(): Boolean = when (state.settings.theme) {
        "dark" -> true
        "light" -> false
        else -> (ime.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    // Graphite Signal tokens (single source of truth: design/GraphiteSignal.kt)
    fun palette(): GraphiteSignal.Palette {
        val base = GraphiteSignal.paletteForTheme(state.settings.theme, isNight())
        val accent = AccentThemes.accentFor(state.settings.accentTheme) ?: return base
        return base.copy(accent = accent, accentPressed = AccentThemes.darken(accent), onAccent = AccentThemes.onAccentFor(accent), focus = accent)
    }
    fun surfaceColor(): Int = palette().keyboardBackground
    fun textColor(): Int = palette().textPrimary
    fun mutedTextColor(): Int = palette().textSecondary
    fun keyColor(): Int = palette().surface
    fun specialKeyColor(): Int = palette().secondarySurface
    fun borderColor(): Int = palette().border
    fun accentColor(): Int = palette().accent
    fun onAccentColor(): Int = palette().onAccent
    fun focusColor(): Int = palette().focus
}

