package com.nefeon.akzsa.keyboard

import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Builds the AI Workspace, Languages, Snippets and voice-session panels. Every colour comes from the active palette. */
internal class ToolPanels(private val hub: KeyboardHub) {
    private val state get() = hub.state
    private val ui get() = hub.ui
    private fun dp(v: Int) = ui.dp(v)

    private fun chipScroller(items: List<View>): HorizontalScrollView {
        val ctx = hub.ime
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        items.forEach { v ->
            row.addView(v, LinearLayout.LayoutParams(-2, dp(36)).apply { marginEnd = dp(8) })
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
    }

    private fun header(title: String, icon: KeyIcon?, onClose: () -> Unit): LinearLayout {
        val ctx = hub.ime
        val p = ui.palette()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (icon != null) addView(IconView(ctx, icon, p.accent, 20f), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            addView(PanelUi.label(ctx, title, 14f, p.textPrimary, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(IconView(ctx, KeyIcon.CLOSE, p.textSecondary, 18f).apply {
                contentDescription = "Close"
                isClickable = true
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener { onClose() }
            })
        }
    }

    // ---- AI Workspace (Grammar | AI Emoji | Reply | Rewrite | Translate | Notes) ----

    fun aiWorkspace(): View {
        val ctx = hub.ime
        val p = ui.palette()
        val card = PanelUi.card(ctx, p) { dp(it) }
        card.addView(header("AKZSA AI Workspace", KeyIcon.SPARKLE) { state.aiPanelOpen = false; hub.render() })

        val tabs = AiTab.values().map { tab ->
            PanelUi.chip(ctx, tab.title, tab == state.aiTab, p, { dp(it) }) { state.aiTab = tab; hub.render() }
        }
        card.addView(chipScroller(tabs), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val hint = when (state.aiTab) {
            AiTab.GRAMMAR -> AiWorkspace.GRAMMAR_HINT
            AiTab.EMOJI -> AiWorkspace.EMOJI_HINT
            else -> AiTools.emptyHint(state.aiTab.task!!)
        }
        card.addView(PanelUi.label(ctx, hint, 12f, p.textSecondary).apply { maxLines = 3 },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val actions: List<View> = when (state.aiTab) {
            AiTab.GRAMMAR -> listOf(PanelUi.chip(ctx, "Fix grammar & spelling", true, p, { dp(it) }) { hub.toolbar.runGrammarFix() })
            AiTab.EMOJI -> listOf(PanelUi.chip(ctx, "Add emoji", true, p, { dp(it) }) { hub.toolbar.runEmojiAdder() })
            else -> {
                val task = state.aiTab.task!!
                AiTools.options(task).map { option ->
                    PanelUi.chip(ctx, option.label, false, p, { dp(it) }) { hub.plans.runAiTool(task, option) }
                }
            }
        }
        card.addView(chipScroller(actions), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return card
    }

    // ---- Languages ----

    fun languages(): View {
        val ctx = hub.ime
        val p = ui.palette()
        val card = PanelUi.card(ctx, p) { dp(it) }
        card.addView(header("Languages", KeyIcon.LANGUAGE) { state.languagesPanelOpen = false; hub.render() })
        val current = state.settings.recognitionLanguage
        val typingHindi = state.hindiLayout
        val chips = LANGUAGE_CHOICES.map { choice ->
            val selected = if (choice.hindiTyping) typingHindi && state.nativeScript == choice.script
            else !typingHindi && (if (choice.name == "English") current.startsWith("en") else current == choice.voiceCode)
            PanelUi.chip(ctx, choice.name, selected, p, { dp(it) }) { hub.toolbar.selectLanguage(choice) }
        }
        card.addView(chipScroller(chips), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        card.addView(PanelUi.label(ctx, "Voice: ${state.settings.languageLabel}", 12f, p.textSecondary),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        return card
    }

    /** Opens [urlPrefix] + the selected text in the browser; only runs when the user taps, and only with selected text. */
    private fun openWithSelection(urlPrefix: String) {
        val selected = hub.inputConnection?.getSelectedText(0)?.toString()?.take(500).orEmpty()
        if (selected.isBlank()) { hub.setStatus("Select some text first"); return }
        try {
            val uri = android.net.Uri.parse(urlPrefix + java.net.URLEncoder.encode(selected, "UTF-8"))
            hub.ime.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            hub.setStatus("No browser available")
        }
    }

    // ---- Snippets ----

    fun snippets(): View {
        val ctx = hub.ime
        val p = ui.palette()
        val card = PanelUi.card(ctx, p) { dp(it) }
        card.addView(header("Snippets", KeyIcon.SNIPPETS) { state.snippetsPanelOpen = false; hub.render() })
        val entries = SnippetDictionary.parse(state.settings.snippetTerms)
        if (entries.isEmpty()) {
            card.addView(PanelUi.label(ctx, "No snippets yet. Add, edit or delete them in the AKZSA app under Settings.", 12f, p.textSecondary),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        val chips = ArrayList<View>()
        chips += PanelUi.chip(ctx, "Date", false, p, { dp(it) }) {
            hub.inputConnection?.commitText(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date()), 1)
        }
        chips += PanelUi.chip(ctx, "Time", false, p, { dp(it) }) {
            hub.inputConnection?.commitText(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date()), 1)
        }
        EmailTemplates.ALL.forEach { (name, body) ->
            chips += PanelUi.chip(ctx, "✉ $name", false, p, { dp(it) }) {
                hub.inputConnection?.commitText(body, 1)
                hub.setStatus("Template inserted")
            }
        }
        chips += PanelUi.chip(ctx, "Format phone", false, p, { dp(it) }) {
            val selected = hub.inputConnection?.getSelectedText(0)?.toString().orEmpty()
            val formatted = PhoneFormat.format(selected)
            if (formatted == null) hub.setStatus("Select a 10-digit phone number first")
            else hub.inputConnection?.commitText(formatted, 1)
        }
        chips += PanelUi.chip(ctx, "Search web", false, p, { dp(it) }) { openWithSelection("https://www.google.com/search?q=") }
        chips += PanelUi.chip(ctx, "Translate", false, p, { dp(it) }) {
            val target = if (state.settings.recognitionLanguage.startsWith("en") || state.settings.recognitionLanguage == "hinglish") "hi"
                else "en"
            openWithSelection("https://translate.google.com/?sl=auto&tl=$target&op=translate&text=")
        }
        entries.forEach { e ->
            chips += PanelUi.chip(ctx, e.trigger.take(24), false, p, { dp(it) }) {
                hub.inputConnection?.commitText(e.expansion, 1)
                hub.setStatus("Snippet inserted")
            }
        }
        chips += PanelUi.chip(ctx, if (entries.isEmpty()) "Add snippet" else "Manage", true, p, { dp(it) }) { hub.toolbar.openApp() }
        card.addView(chipScroller(chips), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return card
    }

    // ---- One Mic session (Listening / Processing) ----

    fun voiceSession(heightPx: Int): View {
        val ctx = hub.ime
        val p = ui.palette()
        val listening = state.listening
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = PanelUi.rounded(p.surface, p.border, dp(16).toFloat(), dp(1))
            minimumHeight = heightPx
        }
        box.addView(PanelUi.label(ctx, if (listening) "Listening…" else "Processing…", 18f, p.textPrimary, bold = true))
        if (listening) {
            box.addView(WaveView(ctx, p.voice, { state.audioLevel }),
                LinearLayout.LayoutParams(dp(220), dp(54)).apply { topMargin = dp(8); bottomMargin = dp(4) })
        } else {
            box.addView(ProgressBar(ctx).apply {
                isIndeterminate = true
                indeterminateTintList = android.content.res.ColorStateList.valueOf(p.voice)
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { topMargin = dp(10); bottomMargin = dp(6) })
        }
        box.addView(PanelUi.label(ctx, state.settings.languageLabel, 13f, p.textSecondary))
        val status = state.status
        if (status.isNotBlank() && status != KeyboardState.IDLE_STATUS) {
            box.addView(PanelUi.label(ctx, status, 12f, p.textSecondary).apply { maxLines = 2; gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        }
        if (listening) {
            box.addView(PanelUi.chip(ctx, "Tap to stop", true, p, { dp(it) }) { hub.dictation.onMicrophoneTapped() },
                LinearLayout.LayoutParams(-2, dp(40)).apply { topMargin = dp(8) })
        }
        return box
    }

    internal data class LanguageChoice(val name: String, val voiceCode: String, val hindiTyping: Boolean, val script: String? = null)

    companion object {
        val LANGUAGE_CHOICES = listOf(
            LanguageChoice("English", "en-IN", false),
            LanguageChoice("Hindi", "hi-IN", true, "hi"),
            LanguageChoice("Hinglish", "hinglish", false),
            LanguageChoice("Kannada", "kn-IN", true, "kn"),
            LanguageChoice("Tamil", "ta-IN", true, "ta"),
            LanguageChoice("Telugu", "te-IN", true, "te"),
            LanguageChoice("Bengali", "bn-IN", true, "bn"),
            LanguageChoice("Marathi", "mr-IN", true, "mr"),
            LanguageChoice("Gujarati", "gu-IN", true, "gu"),
            LanguageChoice("Malayalam", "ml-IN", true, "ml"),
            LanguageChoice("Punjabi", "pa-IN", true, "pa"),
            LanguageChoice("Urdu", "ur-IN", true, "ur"),
            LanguageChoice("Arabic", "ar-SA", true, "ar"),
        )
    }
}
