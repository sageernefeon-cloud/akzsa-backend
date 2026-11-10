package com.nefeon.akzsa.keyboard

import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.nefeon.akzsa.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Toolbar state and actions: the seven toolbar icons (Grammar Fix, Clipboard, Emoji, GIF, Snippets, Languages,
 * Settings), the panel toggles (only one panel is open at a time), language selection and the settings shortcut.
 * [KeyboardRenderer] asks this class for the buttons and only draws them.
 */
internal class ToolbarController(private val hub: KeyboardHub) {
    private val state get() = hub.state

    val gifController = GifPanel.GifController()

    /** One icon button of the toolbar. */
    data class ToolButton(val label: String, val description: String, val icon: KeyIcon, val selected: Boolean, val action: () -> Unit)

    fun toolButtons(): List<ToolButton> = ToolbarOrder.apply(defaultToolButtons(), state.settings.toolbarOrder) { it.label }

    private fun defaultToolButtons(): List<ToolButton> = listOf(
        // The sparkle opens Grammar Fix (the AI Emoji Adder is a separate tab inside the AI Workspace).
        ToolButton("Grammar", "Grammar Fix", KeyIcon.SPARKLE, state.aiPanelOpen) { openGrammarFix() },
        ToolButton("Clipboard", "Clipboard", KeyIcon.CLIPBOARD, state.clipboardRowOpen) { togglePanel(state::clipboardRowOpen) },
        ToolButton("Emoji", "Emoji", KeyIcon.EMOJI, state.emojiPanelOpen) { toggleEmojiPanel() },
        ToolButton("GIF", "GIF", KeyIcon.GIF, state.gifPanelOpen) { openGifPicker() },
        ToolButton("Snippets", "Snippets", KeyIcon.SNIPPETS, state.snippetsPanelOpen) { togglePanel(state::snippetsPanelOpen) },
        ToolButton("Lang", "Languages", KeyIcon.LANGUAGE, state.languagesPanelOpen) { togglePanel(state::languagesPanelOpen) },
        ToolButton("Settings", "Settings", KeyIcon.SETTINGS, false) { openApp() },
    )

    // ---- panels: only one open at a time ----

    private fun closeAllPanels() {
        state.emojiPanelOpen = false
        state.emojiSearching = false
        state.emojiQuery = ""
        state.clipSearching = false
        state.clipQuery = ""
        state.cursorRowOpen = false
        state.clipboardRowOpen = false
        state.gifPanelOpen = false
        state.snippetsPanelOpen = false
        state.languagesPanelOpen = false
        state.aiPanelOpen = false
    }

    /** Opens the panel behind [flag] with every other panel closed, or closes it if it was already open. */
    private fun togglePanel(flag: kotlin.reflect.KMutableProperty0<Boolean>) {
        if (state.passwordField) { hub.setStatus("Panels are disabled in protected fields"); return }
        val wasOpen = flag.get()
        closeAllPanels()
        flag.set(!wasOpen)
        hub.render()
    }

    /** The "+" button in the input strip: cursor, selection and clipboard-edit controls. */
    fun toggleCursorRow() = togglePanel(state::cursorRowOpen)

    /** Emoji shortcut on the bottom row of the keys. */
    fun openEmoji() = toggleEmojiPanel()

    private fun toggleEmojiPanel() {
        if (state.passwordField) { hub.setStatus("Emoji panel is disabled in protected fields"); return }
        togglePanel(state::emojiPanelOpen)
    }

    /** Toggles the inline GIPHY GIF panel. */
    private fun openGifPicker() {
        if (state.passwordField) { hub.setStatus("GIF panel disabled in protected fields"); return }
        togglePanel(state::gifPanelOpen)
        if (!state.gifPanelOpen) gifController.query = ""
    }

    // ---- AI: Grammar Fix and AI Emoji Adder ----

    /** The sparkle: opens the AI Workspace on the Grammar tab and immediately prepares a reviewable fix. */
    private fun openGrammarFix() {
        if (state.passwordField) { hub.setStatus("AI tools are disabled in protected fields"); return }
        if (state.aiPanelOpen && state.aiTab == AiTab.GRAMMAR) { closeAllPanels(); hub.render(); return }
        closeAllPanels()
        state.aiPanelOpen = true
        state.aiTab = AiTab.GRAMMAR
        hub.render()
        runGrammarFix()
    }

    fun runGrammarFix() = hub.plans.runAiTool(AiTask.REWRITE, AiWorkspace.GRAMMAR_OPTION)

    fun runEmojiAdder() = hub.plans.transformWholeField(AiWorkspace.EMOJI_INSTRUCTION)

    /** Closes the transient panels when the keyboard is hidden or focus moves to another field. */
    fun closeTransientPanels() {
        closeAllPanels()
        gifController.query = ""
    }

    // ---- language, settings ----

    /** Picks the voice language and, for Hindi, the Devanagari typing layer. Hinglish is typed with the English layer. */
    fun selectLanguage(choice: ToolPanels.LanguageChoice) {
        hub.scope.launch {
            val current = state.settings.recognitionLanguage
            val code = if (choice.name == "English" && current.startsWith("en")) current else choice.voiceCode
            hub.settingsRepository.update { it.copy(recognitionLanguage = code) }
            state.settings = state.settings.copy(recognitionLanguage = code)
            state.hindiLayout = choice.hindiTyping
            state.nativeScript = choice.script ?: "hi"
            state.hindiPage = 0
            state.symbolsMode = false
            hub.setStatus("Language: ${state.settings.languageLabel}")
            hub.render()
        }
    }

    fun openApp() {
        try {
            hub.ime.startActivity(Intent(hub.ime, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (e: Exception) { KeyboardLog.w("ToolbarController", e);
            Toast.makeText(hub.ime, "Open AKZSA from your app list to change settings.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Downloads the chosen GIF and sends it to the focused app; falls back to copying the link. */
    fun sendGif(gif: GifPanel.GifResult) {
        hub.setStatus("Preparing GIF…")
        val accepts = GifInserter.editorAcceptsGif(hub.editorInfo)
        val appContext = hub.ime.applicationContext
        hub.scope.launch {
            val file = if (accepts) {
                withContext(Dispatchers.IO) { GifInserter.download(appContext, gif.url) }
            } else null
            val sent = file != null && GifInserter.commit(appContext, hub.inputConnection, hub.editorInfo, file, gif.title)
            if (sent) {
                hub.setStatus("GIF sent")
                state.gifPanelOpen = false
                gifController.query = ""
                hub.render()
            } else {
                val copied = GifInserter.copyLink(appContext, gif.url, gif.title)
                hub.setStatus(
                    if (copied) "This app does not accept GIFs from keyboards · link copied, paste it"
                    else "Could not send the GIF"
                )
            }
        }
    }
}
