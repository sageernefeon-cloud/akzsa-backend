package com.nefeon.akzsa.keyboard

import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.nefeon.akzsa.R
import com.nefeon.akzsa.commands.ActionPlan
import com.nefeon.akzsa.commands.PlanKind
import com.nefeon.akzsa.network.ApiResult
import com.nefeon.akzsa.network.BackendClient
import com.nefeon.akzsa.security.InstallId
import java.util.Locale

/** One key as the layout code describes it; [KeyboardRenderer] turns it into a [KeyView]. */
internal data class Key(
    val label: String,
    val weight: Float = 1f,
    val special: Boolean = false,
    /** Plain character keys type on touch-down (instant feel). Others fire on release, only if the finger is still on the key. */
    val commitOnDown: Boolean = false,
    /** Lower-case form of a letter key. Set for keys whose label follows Shift; the label is re-derived without a full render. */
    val caseBase: String? = null,
    /** Draws this glyph instead of the text label. */
    val icon: KeyIcon? = null,
    /** Fixed width in dp (round mic button); null = share the row by [weight]. */
    val widthDp: Int? = null,
    /** Overrides the style the renderer would pick from the label. */
    val style: KeyStyle? = null,
    val action: () -> Unit,
)

/**
 * All view building for the keyboard. The static outer frame comes from `res/layout/keyboard_frame.xml`
 * (inflated once); the toolbar slot, panel slot and key-rows container are cleared and refilled programmatically
 * on every [render] because their content depends on layout, shift, mode and settings.
 */
internal class KeyboardRenderer(private val hub: KeyboardHub) {
    private val state get() = hub.state
    private val ui get() = hub.ui
    private val ime get() = hub.ime

    private class Frame(
        val root: LinearLayout,
        val header: TextView,
        val stripSlot: LinearLayout,
        val toolbarSlot: LinearLayout,
        val chipsSlot: LinearLayout,
        val panelSlot: LinearLayout,
        val previewCard: LinearLayout,
        val keyRows: LinearLayout,
    )

    private var frame: Frame? = null
    private val panels by lazy { ToolPanels(hub) }

    private val popup = KeyPopup(ime)
    /** Letter keys currently on screen with their lower-case base, so Shift can relabel them without rebuilding the keyboard. */
    private val letterKeys = mutableListOf<Pair<KeyView, String>>()
    private var shiftKeyView: KeyView? = null
    private var stripView: TextView? = null

    /** Compact layouts hide the status line (null) so [setStatus] knows to fall back to a toast for problems. */
    private var statusView: TextView? = null

    /** Inflates the static frame once and fills it. Called from `onCreateInputView`. */
    fun inflateFrame(): View {
        val root = LayoutInflater.from(ime).inflate(R.layout.keyboard_frame, null, false) as LinearLayout
        frame = Frame(
            root = root,
            header = root.findViewById<TextView>(R.id.keyboard_status),
            stripSlot = root.findViewById<LinearLayout>(R.id.keyboard_strip_slot),
            toolbarSlot = root.findViewById<LinearLayout>(R.id.keyboard_toolbar_slot),
            chipsSlot = root.findViewById<LinearLayout>(R.id.keyboard_chips_slot),
            panelSlot = root.findViewById<LinearLayout>(R.id.keyboard_panel_slot),
            previewCard = root.findViewById<LinearLayout>(R.id.keyboard_preview_card),
            keyRows = root.findViewById<LinearLayout>(R.id.keyboard_rows),
        )
        render()
        return root
    }

    fun render() {
        val f = frame ?: return
        hub.typing.stopBackspaceRepeat()
        popup.dismiss()
        letterKeys.clear()
        shiftKeyView = null
        f.stripSlot.removeAllViews()
        f.toolbarSlot.removeAllViews()
        f.chipsSlot.removeAllViews()
        f.panelSlot.removeAllViews()
        f.keyRows.removeAllViews()
        f.root.setBackgroundColor(ui.surfaceColor())
        recomputeLayout()
        applyRootPadding(f.root)
        bindHeader(f)

        val m = state.kbMetrics
        addStrip(f.stripSlot)
        addToolbar(f.toolbarSlot)
        f.chipsSlot.visibility = if (m.collapseLevel >= 2) View.GONE else View.VISIBLE
        refreshStrip()

        addPanels(f.panelSlot)
        bindPreview(f)

        val inputType = hub.editorInfo?.inputType ?: InputType.TYPE_CLASS_TEXT
        if (state.listening || state.cloudUploadInProgress) {
            val rowsHeight = ui.dp(typingRowCount() * (m.keyRowHeightDp + m.keyGapDp))
            f.keyRows.addView(panels.voiceSession(rowsHeight), LinearLayout.LayoutParams(-1, rowsHeight))
        } else {
            addTypingRows(f.keyRows, inputType)
        }
        fadeIn(f.panelSlot)
    }

    private fun fadeIn(view: ViewGroup) {
        if (view.childCount == 0) return
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(160).start()
    }

    // ---- input strip (+ / what you are typing / send) ----

    private fun addStrip(slot: LinearLayout) {
        val p = ui.palette()
        val gap = ui.dp(maxOf(1, state.kbMetrics.keyGapDp / 2))
        val h = ui.dp(STRIP_HEIGHT_DP)
        val plus = iconButton(KeyIcon.PLUS, "Cursor and selection controls", KeyStyle.NORMAL, h) { hub.toolbar.toggleCursorRow() }
        slot.addView(plus, LinearLayout.LayoutParams(h, h).apply { marginEnd = gap * 2 })

        val strip = TextView(ime).apply {
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(14), 0, ui.dp(14), 0)
            background = PanelUi.rounded(p.surface, p.border, h / 2f, ui.dp(1))
            contentDescription = "Text you are typing"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        stripView = strip
        slot.addView(strip, LinearLayout.LayoutParams(0, h, 1f))

        val send = iconButton(KeyIcon.SEND, "Send", KeyStyle.ACCENT, h) { hub.typing.pressEnter() }
        slot.addView(send, LinearLayout.LayoutParams(h, h).apply { marginStart = gap * 2 })
        slot.layoutParams = LinearLayout.LayoutParams(-1, h + 2 * gap).also { it.bottomMargin = gap }
    }

    /** Round icon-only button used in the strip. */
    private fun iconButton(icon: KeyIcon, description: String, style: KeyStyle, sizePx: Int, onClick: () -> Unit): KeyView =
        KeyView(ime).apply {
            this.icon = icon
            iconSizeDp = 20f
            bind(style, ui.palette(), cornerRadiusDp = 40, shadow = false, insetDp = 0)
            contentDescription = description
            isHapticFeedbackEnabled = state.settings.hapticFeedback
            setOnClickListener {
                if (state.settings.hapticFeedback) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }

    /**
     * Updates the strip text and the suggestion chips in place (no full render), because this runs on every cursor
     * move. Protected fields are never read.
     */
    fun refreshStrip() {
        val f = frame ?: return
        val strip = stripView ?: return
        val p = ui.palette()
        if (state.passwordField) {
            strip.text = "Protected field"
            strip.setTextColor(p.textSecondary)
            f.chipsSlot.removeAllViews()
            return
        }
        val before = try { hub.inputConnection?.getTextBeforeCursor(StripText.MAX * 2, 0) } catch (e: Exception) { null }
        val shown = StripText.compose(before)
        if (shown.isEmpty()) {
            strip.text = "Start typing…"
            strip.setTextColor(p.textSecondary)
        } else {
            strip.text = shown
            strip.setTextColor(p.textPrimary)
        }
        bindChips(f.chipsSlot, before)
    }

    private fun bindChips(slot: LinearLayout, before: CharSequence?) {
        slot.removeAllViews()
        val p = ui.palette()
        val h = ui.dp(CHIP_ROW_DP)
        slot.layoutParams = LinearLayout.LayoutParams(-1, h)
        val suggestions = Suggestions.build(before, state.settings.dictionaryTerms, state.settings.snippetTerms, ClipboardHistory.recent(1).firstOrNull(),
            smart = state.settings.smartSuggestions && !state.passwordField && state.settings.recognitionLanguage.startsWith("en"))
        val gap = ui.dp(6)
        suggestions.forEach { sug ->
            val chip = PanelUi.chip(ime, sug.label, false, p, { ui.dp(it) }) {
                val ic = hub.inputConnection ?: return@chip
                if (sug.replaceLength > 0) ic.deleteSurroundingText(sug.replaceLength, 0)
                ic.commitText(sug.insert, 1)
            }
            chip.contentDescription = "Suggestion: ${sug.label}"
            slot.addView(chip, LinearLayout.LayoutParams(0, ui.dp(32), 1f).apply { marginEnd = gap })
        }
    }

    // ---- toolbar (7 icons: share the row when they fit, scroll with a visible peek when they do not) ----

    /** Scroll position of the toolbar, kept across re-renders so a tap at the far end does not jump the row back. */
    private var toolbarScrollX = 0

    private fun addToolbar(slot: LinearLayout) {
        val m = state.kbMetrics
        val buttons = hub.toolbar.toolButtons()
        val density = ime.resources.displayMetrics.density
        val hPad = ui.dp(m.horizontalPaddingDp)
        val heightPx = ui.dp(m.toolbarHeightDp)
        fun widestDp(sp: Float): Float =
            buttons.maxOf { ToolbarItemView.labelWidthPx(ime, it.label, sp) } / density

        val layout = ToolbarLayoutPolicy.compute(
            availableWidthDp = (m.contentWidthDp - 2 * m.horizontalPaddingDp).toFloat(),
            itemCount = buttons.size,
            widestLabelDp = widestDp(ToolbarLayoutPolicy.MIN_LABEL_SP),
        )
        val labelSp = ToolbarLayoutPolicy.pickLabelSp(layout.cellWidthDp, ::widestDp)

        val row = LinearLayout(ime).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.forEach { b ->
            val item = ToolbarItemView(ime).apply {
                label = b.label
                icon = b.icon
                labelSizeSp = labelSp
                bind(b.selected, ui.palette())
                contentDescription = b.description
                isHapticFeedbackEnabled = state.settings.hapticFeedback
                setOnClickListener {
                    if (state.settings.hapticFeedback) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    b.action()
                }
            }
            val lp = if (layout.scrolls) LinearLayout.LayoutParams(Math.round(layout.cellWidthDp * density), heightPx)
            else LinearLayout.LayoutParams(0, heightPx, 1f)
            row.addView(item, lp)
        }

        if (!layout.scrolls) {
            // Everything fits: equal cells inside the horizontal padding, nothing can be cut off.
            slot.addView(row, LinearLayout.LayoutParams(-1, heightPx).apply { marginStart = hPad; marginEnd = hPad })
            return
        }
        val scroller = HorizontalScrollView(ime).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            // The edge fade only appears on a side that still has hidden content: the clear "more" hint.
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(ui.dp(24))
            clipToPadding = false
            setPadding(hPad, 0, hPad, 0)
            addView(row)
            setOnScrollChangeListener { _, x, _, _, _ -> toolbarScrollX = x }
            addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    v.removeOnLayoutChangeListener(this)
                    (v as HorizontalScrollView).scrollTo(toolbarScrollX, 0)
                }
            })
        }
        slot.addView(scroller, LinearLayout.LayoutParams(-1, heightPx))
    }

    // ---- status header ----

    private fun isIdleStatus(message: String): Boolean =
        message == KeyboardState.IDLE_STATUS || message == "AKZSA · speak naturally" || message.isBlank()

    private fun statusColor(message: String): Int =
        if (PROBLEM_WORDS.containsMatchIn(message)) ui.palette().error else ui.mutedTextColor()

    private fun bindHeader(f: Frame) {
        val m = state.kbMetrics
        if (m.showHeader) {
            f.header.apply {
                text = state.status
                textSize = 12f
                setTextColor(statusColor(state.status))
                visibility = if (isIdleStatus(state.status)) View.GONE else View.VISIBLE
                layoutParams = LinearLayout.LayoutParams(-1, ui.dp(m.headerHeightDp))
            }
            statusView = f.header
        } else {
            // Low-height mode: state is carried by the toolbar; problems still surface as a toast.
            f.header.visibility = View.GONE
            statusView = null
        }
    }

    fun setStatus(message: String) {
        state.status = message
        val idle = isIdleStatus(message)
        val header = statusView
        if (header != null) {
            if (idle) {
                header.visibility = View.GONE
            } else {
                header.visibility = View.VISIBLE
                header.text = message
                header.setTextColor(statusColor(message))
                header.textSize = if (message.length > 80) 10.5f else 12f
            }
        } else if (!idle && PROBLEM_WORDS.containsMatchIn(message)) {
            // Compact layouts hide the status line; problems must still be visible.
            Toast.makeText(ime, message.take(120), Toast.LENGTH_SHORT).show()
        }
    }

    // ---- panels (emoji / cursor / clipboard / GIF) ----

    private fun addPanels(slot: LinearLayout) {
        val s = state
        if (s.aiPanelOpen && !s.passwordField) slot.addView(panels.aiWorkspace(), panelParams())
        if (s.languagesPanelOpen && !s.passwordField) slot.addView(panels.languages(), panelParams())
        if (s.snippetsPanelOpen && !s.passwordField) slot.addView(panels.snippets(), panelParams())
        if (s.emojiPanelOpen && !s.passwordField) {
            val emojiPanel = EmojiPanel.build(
                context = ime,
                dp = { ui.dp(it) },
                textColor = { ui.textColor() },
                keyColor = { ui.keyColor() },
                borderColor = { ui.borderColor() },
                accentColor = { ui.accentColor() },
                onAccentColor = { ui.onAccentColor() },
                searching = s.emojiSearching,
                query = s.emojiQuery,
                remember = !s.settings.incognito,
                onEmojiSelected = { emoji -> hub.inputConnection?.commitText(emoji, 1) },
                onToggleSearch = { s.emojiSearching = !s.emojiSearching; s.emojiQuery = ""; render() },
                onStatus = { message -> setStatus(message) },
                onClose = { s.emojiPanelOpen = false; s.emojiSearching = false; s.emojiQuery = ""; render() },
            )
            slot.addView(emojiPanel, LinearLayout.LayoutParams(-1, -2))
        }

        if (s.cursorRowOpen && !s.passwordField) {
            val cursorRow = CursorRow.build(
                context = ime,
                dp = { ui.dp(it) },
                textColor = { ui.textColor() },
                specialKeyColor = { ui.specialKeyColor() },
                borderColor = { ui.borderColor() },
                getInputConnection = { hub.inputConnection },
                onSelectAll = {
                    hub.inputConnection?.performContextMenuAction(android.R.id.selectAll)
                    setStatus("All text selected")
                },
                onCopy = {
                    hub.inputConnection?.performContextMenuAction(android.R.id.copy)
                    setStatus("Copied to clipboard")
                },
                onPaste = {
                    hub.inputConnection?.performContextMenuAction(android.R.id.paste)
                    setStatus("Pasted from clipboard")
                },
                onCut = {
                    hub.inputConnection?.performContextMenuAction(android.R.id.cut)
                    setStatus("Cut to clipboard")
                },
            )
            slot.addView(cursorRow, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
            })
            val editRow = CursorRow.buildEditRow(
                context = ime,
                dp = { ui.dp(it) },
                textColor = { ui.textColor() },
                specialKeyColor = { ui.specialKeyColor() },
                borderColor = { ui.borderColor() },
                onSelectWord = { if (!hub.typing.selectAroundCursor(TypingController.SelectUnit.WORD)) setStatus("Nothing to select here") },
                onSelectSentence = { if (!hub.typing.selectAroundCursor(TypingController.SelectUnit.SENTENCE)) setStatus("Nothing to select here") },
                onDeleteWord = { hub.typing.deleteWord() },
                onUndo = { hub.typing.undoRedo(redo = false) },
                onRedo = { hub.typing.undoRedo(redo = true) },
            )
            slot.addView(editRow, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(ui.dp(2), 0, ui.dp(2), ui.dp(2))
            })
            val editRow2 = CursorRow.buildEditRow2(
                context = ime,
                dp = { ui.dp(it) },
                textColor = { ui.textColor() },
                specialKeyColor = { ui.specialKeyColor() },
                borderColor = { ui.borderColor() },
                onSelectParagraph = { if (!hub.typing.selectAroundCursor(TypingController.SelectUnit.PARAGRAPH)) setStatus("Nothing to select here") },
                onExtendLeft = { hub.typing.extendSelection(-1) },
                onExtendRight = { hub.typing.extendSelection(1) },
                onDeleteSentence = { hub.typing.deleteSentence() },
            )
            slot.addView(editRow2, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(ui.dp(2), 0, ui.dp(2), ui.dp(2))
            })
        }

        if (s.clipboardRowOpen && !s.passwordField) {
            val clipRow = ClipboardRow.build(
                context = ime,
                dp = { ui.dp(it) },
                textColor = { ui.textColor() },
                keyColor = { ui.keyColor() },
                borderColor = { ui.borderColor() },
                accentColor = { ui.accentColor() },
                items = ClipboardLogic.filter(ClipboardLogic.search(ClipboardHistory.ordered(), s.clipQuery), s.clipFilter),
                searching = s.clipSearching,
                query = s.clipQuery,
                filterLabel = s.clipFilter,
                onToggleSearch = { s.clipSearching = !s.clipSearching; s.clipQuery = ""; render() },
                onCycleFilter = {
                    val i = ClipboardLogic.FILTERS.indexOf(s.clipFilter)
                    s.clipFilter = ClipboardLogic.FILTERS[(i + 1) % ClipboardLogic.FILTERS.size]
                    render()
                },
                onEdit = { text -> hub.inputConnection?.commitText(text, 1); ClipboardHistory.remove(text); setStatus("Moved to the text field to edit"); render() },
                onPaste = { text ->
                    hub.typing.pasteSmart(text)
                    setStatus("Clipboard item inserted")
                },
                onTogglePin = { text -> ClipboardHistory.togglePin(text); render() },
                onDelete = { text -> ClipboardHistory.remove(text); render() },
                onClear = {
                    ClipboardHistory.clear()
                    s.clipboardRowOpen = false
                    setStatus("Clipboard history cleared")
                    render()
                },
            )
            if (clipRow != null) {
                slot.addView(clipRow, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
                })
            } else {
                setStatus("Clipboard is empty")
                s.clipboardRowOpen = false
            }
        }

        if (s.gifPanelOpen && !s.passwordField) {
            val gifPanel = GifPanel.build(
                context = ime,
                scope = hub.scope,
                controller = hub.toolbar.gifController,
                dp = { ui.dp(it) },
                fetchGifs = { query, stickers ->
                    val token = hub.tokenStore.readTokenAsync()
                    if (token.isNullOrBlank()) {
                        Result.failure(IllegalStateException("Add the backend token in AKZSA Settings to use GIFs"))
                    } else when (val reply = BackendClient.searchGifs(s.settings.backendBaseUrl, token, query, stickers, InstallId.get(ime.applicationContext))) {
                        is ApiResult.Failure -> Result.failure(IllegalStateException(reply.message))
                        is ApiResult.Success -> Result.success(reply.value.results.filter { it.url.isNotBlank() }.map {
                            GifPanel.GifResult(it.url, it.preview_url.ifBlank { it.url }, it.title)
                        })
                    }
                },
                textColor = { ui.textColor() },
                keyColor = { ui.keyColor() },
                borderColor = { ui.borderColor() },
                accentColor = { ui.accentColor() },
                onAccentColor = { ui.onAccentColor() },
                onGifSelected = { gif ->
                    if (!s.settings.incognito) GifStore.recordUse(ime, gif)
                    hub.toolbar.sendGif(gif)
                },
                onClose = { s.gifPanelOpen = false; render() },
            )
            slot.addView(gifPanel, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
            })
        }
    }

    private fun panelParams() = LinearLayout.LayoutParams(-1, -2).apply { setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2)) }

    // ---- pending-action preview (Before / After, Cancel / Regenerate / Apply) ----

    private fun bindPreview(f: Frame) {
        f.previewCard.removeAllViews()
        f.previewCard.visibility = View.GONE
        f.keyRows.visibility = View.VISIBLE
        hub.plans.updatePendingPreview()
    }

    /**
     * Shows (or hides, for `null`) the result of a pending AI action as a card: what was there before, what it would
     * become, and Cancel / Regenerate / Apply. While it is showing it replaces the key rows; nothing in the field changes
     * until Apply is tapped.
     */
    fun showPendingPreview(plan: ActionPlan?) {
        val f = frame ?: return
        f.previewCard.removeAllViews()
        if (plan == null) {
            f.previewCard.visibility = View.GONE
            f.keyRows.visibility = View.VISIBLE
            return
        }
        val p = ui.palette()
        val card = PanelUi.card(ime, p) { ui.dp(it) }
        val grammar = plan.explanation == AiTools.describe(AiTask.REWRITE, AiWorkspace.GRAMMAR_OPTION)
        val head = LinearLayout(ime).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(IconView(ime, if (grammar) KeyIcon.SPARKLE else KeyIcon.CHECK, p.accent, 20f),
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(8) })
        val titleBox = LinearLayout(ime).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(PanelUi.label(ime, if (grammar) "Grammar Fix" else plan.explanation, 14f, p.textPrimary, bold = true))
        if (grammar) titleBox.addView(PanelUi.label(ime, "Spelling, grammar and punctuation", 11f, p.textSecondary))
        head.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(head)

        val before: String?
        val after: String?
        when (plan.kind) {
            PlanKind.REPLACE_SELECTED_TEXT -> { before = plan.sourceSelection; after = plan.replacementText }
            PlanKind.INSERT_GENERATED_TEXT, PlanKind.DICTATION -> { before = null; after = plan.replacementText }
            else -> { before = null; after = null }
        }
        fun section(title: String, text: String) {
            card.addView(PanelUi.label(ime, title, 12f, p.textSecondary, bold = true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
            card.addView(PanelUi.textBox(ime, text.take(PREVIEW_CHARS) + if (text.length > PREVIEW_CHARS) "…" else "", p, { ui.dp(it) }, maxLines = 2),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(2) })
        }
        if (!before.isNullOrBlank()) section("Before", before)
        if (!after.isNullOrBlank()) section(if (before.isNullOrBlank()) "Result" else "After", after)
        if (before.isNullOrBlank() && after.isNullOrBlank()) {
            card.addView(PanelUi.label(ime, plan.explanation, 13f, p.textPrimary),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
        }

        val actions = LinearLayout(ime).apply { orientation = LinearLayout.VERTICAL }
        val keys = mutableListOf(Key("Cancel", 1f, true) { hub.plans.cancelPendingAction() })
        state.regenerate?.let { again -> keys += Key("Regenerate", 1.3f, true) { hub.plans.clearPending(); again() } }
        keys += Key("Apply", 1f) { hub.plans.confirmPendingAction() }
        addWeightedRow(actions, keys, state.kbMetrics.actionRowHeightDp - 4)
        card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })

        f.previewCard.addView(card, LinearLayout.LayoutParams(-1, -2))
        f.previewCard.visibility = View.VISIBLE
        f.keyRows.visibility = View.GONE
        f.previewCard.alpha = 0f
        f.previewCard.animate().alpha(1f).setDuration(160).start()
    }

    // ---- typing rows (layout logic unchanged) ----

    private fun addTypingRows(rows: LinearLayout, inputType: Int) {
        val s = state
        val rowHeight = s.kbMetrics.keyRowHeightDp
        val typing = hub.typing
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val numeric = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE || inputClass == InputType.TYPE_CLASS_DATETIME
        if (numeric) {
            addCharRow(rows, listOf("1", "2", "3"))
            addCharRow(rows, listOf("4", "5", "6"))
            addCharRow(rows, listOf("7", "8", "9"))
            addWeightedRow(rows, listOf(
                charKey("-"), charKey("+"), charKey("0"), charKey("."),
                backspaceKey(1.2f),
            ), rowHeight)
            addWeightedRow(rows, listOf(
                charKey("*"), charKey("#"),
                Key(enterKeyLabel(inputType), 2f, true) { typing.pressEnter() },
            ), rowHeight)
        } else if (s.symbolsMode) {
            val page2 = s.symbolsPage == 1
            if (!page2) {
                addCharRow(rows, listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
                addCharRow(rows, listOf("@", "#", "\$", "%", "&", "-", "+", "*", "!", "?"))
            } else {
                addCharRow(rows, listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"))
                addCharRow(rows, listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"))
            }
            val third = (if (page2) listOf("[", "]", "<", ">", "©", "®", "™") else listOf("/", "(", ")", ":", ";")).map { charKey(it) }
            val pageKey = Key(if (page2) "?123" else "=<", 1.3f, true) { s.symbolsPage = 1 - s.symbolsPage; render() }
            addWeightedRow(rows, listOf(pageKey) + third + backspaceKey(1.3f), rowHeight)
            addBottomRows(rows, inputType)
        } else if (s.hindiLayout) {
            val layoutRows = NativeLayouts.rows(s.nativeScript, s.hindiPage)
            layoutRows.forEachIndexed { index, chars ->
                if (index == layoutRows.lastIndex) {
                    val keys = chars.map { c -> charKey(c) }.toMutableList()
                    keys.add(0, Key(NativeLayouts.toggleLabel(s.nativeScript, s.hindiPage), 1.3f, true) { s.hindiPage = 1 - s.hindiPage; render() })
                    keys.add(backspaceKey(1.3f))
                    addWeightedRow(rows, keys, rowHeight)
                } else {
                    addCharRow(rows, chars)
                }
            }
            addBottomRows(rows, inputType)
        } else {
            if (s.settings.showNumberRow) addCharRow(rows, "1234567890".map { it.toString() })
            addCharRow(rows, "qwertyuiop".map { it.toString() })
            addCharRow(rows, "asdfghjkl".map { it.toString() })
            val third = mutableListOf<Key>(
                Key(if (s.capsLock) "⇪" else "⇧", 1.4f, true, icon = shiftIcon()) { typing.onShiftTapped() },
            )
            "zxcvbnm".forEach { c -> third.add(letterKey(c.toString())) }
            third.add(backspaceKey(1.4f))
            addWeightedRow(rows, third, rowHeight)
            addBottomRows(rows, inputType)
        }
    }

    private fun shiftIcon(): KeyIcon = if (state.capsLock || state.shiftEnabled) KeyIcon.CAPS else KeyIcon.SHIFT

    private fun backspaceKey(weight: Float) = Key("⌫", weight, true, icon = KeyIcon.BACKSPACE) { hub.typing.backspace() }

    /**
     * Two bottom rows, matching the AKZSA design:
     *   ?123 | emoji | , | space (AKZSA) | . | enter
     *   hide | space ~~ | MIC | ~~ space | switch keyboard
     * Both waveform zones beside the microphone insert one space on tap; only the round microphone starts voice input.
     */
    private fun addBottomRows(rows: LinearLayout, inputType: Int) {
        val s = state
        val typing = hub.typing
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isEmail = inputClass == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val isUri = inputClass == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_URI
        val rowHeight = s.kbMetrics.keyRowHeightDp

        val bottom = mutableListOf<Key>()
        bottom += Key(if (s.symbolsMode) "ABC" else "?123", 1.3f, true) { s.symbolsMode = !s.symbolsMode; s.symbolsPage = 0; render() }
        bottom += Key("😊", 1.1f, true, icon = KeyIcon.EMOJI) { hub.toolbar.openEmoji() }
        bottom += charKey(",", 0.8f)
        if (isEmail) bottom += charKey("@", 0.8f)
        if (isUri) bottom += Key(".com", 1.0f, commitOnDown = true) { typing.typeText(".com") }
        bottom += Key("space", 3.4f, commitOnDown = true) { typing.typeText(" ") }
        bottom += if (s.hindiLayout && !s.symbolsMode) charKey("।", 0.8f) else charKey(".", 0.8f)
        bottom += Key(enterKeyLabel(inputType), 1.4f, true, icon = if (enterKeyLabel(inputType) == "↵") KeyIcon.ENTER else null) { typing.pressEnter() }
        addWeightedRow(rows, bottom, rowHeight)

        val micHeight = rowHeight + 6
        val micIcon = if (s.listening) KeyIcon.STOP else KeyIcon.MIC
        val voice = mutableListOf<Key>()
        voice += Key("hide", 0.9f, true, icon = KeyIcon.CHEVRON_DOWN, style = KeyStyle.TOOL) { hub.ime.requestHideSelf(0) }
        voice += Key("space", 1.7f, commitOnDown = true, icon = KeyIcon.WAVE_LEFT, style = KeyStyle.TOOL) { typing.typeText(" ") }
        voice += Key("mic", 1f, true, icon = micIcon, widthDp = micHeight, style = KeyStyle.VOICE) { hub.dictation.onMicrophoneTapped() }
        voice += Key("space", 1.7f, commitOnDown = true, icon = KeyIcon.WAVE_RIGHT, style = KeyStyle.TOOL) { typing.typeText(" ") }
        voice += Key("switch", 0.9f, true, icon = KeyIcon.KEYBOARD, style = KeyStyle.TOOL) {
            if (typing.canSwitchKeyboard()) typing.switchKeyboard() else hub.setStatus("No other keyboard is enabled to switch to")
        }
        addWeightedRow(rows, voice, micHeight)
    }

    // Delegates to EnterKeyPolicy so the label can never disagree with what pressEnter()
    // (in TypingController, via the same EnterKeyPolicy.decide) actually does.
    private fun enterKeyLabel(inputType: Int): String =
        EnterKeyPolicy.label(inputType, hub.editorInfo?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED)

    private fun addCharRow(rows: LinearLayout, chars: List<String>) {
        addWeightedRow(rows, chars.map { char -> if (char.uppercase(Locale.ROOT) != char) letterKey(char) else charKey(char) },
            state.kbMetrics.keyRowHeightDp)
    }

    private fun shifted(base: String): String = if (state.shiftEnabled) base.uppercase(Locale.ROOT) else base

    /** A fixed character (digit, symbol, punctuation, Devanagari): types on touch-down. */
    private fun charKey(c: String, weight: Float = 1f) = Key(c, weight, commitOnDown = true) { hub.typing.typeText(c) }

    /** A letter whose label follows Shift. Its label is refreshed in place by [refreshShiftState]. */
    private fun letterKey(base: String) = Key(shifted(base), 1f, commitOnDown = true, caseBase = base) { hub.typing.typeText(shifted(base)) }

    /**
     * Shift or Caps Lock changed: relabel the letter keys and the Shift key in place. This replaces a full
     * [render], which rebuilt every key view (and could drop touches) each time a sentence ended.
     */
    fun refreshShiftState() {
        for ((view, base) in letterKeys) {
            val label = shifted(base)
            if (view.text.toString() != label) {
                view.text = label
                view.contentDescription = KeyLabels.describe(label)
            }
        }
        shiftKeyView?.let { view ->
            val glyph = shiftIcon()
            if (view.icon != glyph) {
                view.icon = glyph
                view.contentDescription = KeyLabels.describe(if (state.capsLock) "⇪" else "⇧")
            }
        }
    }

    // ---- rows and single keys ----

    private fun addWeightedRow(parent: LinearLayout, keys: List<Key>, heightDp: Int, toolbar: Boolean = false) {
        val row = LinearLayout(ime).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        // Keys fill their whole grid cell (no margins) and draw inset by the gap, so there are no dead zones
        // between keys: a touch that lands "between" two keys still belongs to the nearest one.
        val gap = ui.dp(maxOf(1, state.kbMetrics.keyGapDp / 2))
        keys.forEach { key ->
            val fixed = key.widthDp
            val lp = if (fixed != null) LinearLayout.LayoutParams(ui.dp(fixed) + 2 * gap, ui.dp(heightDp) + 2 * gap)
            else LinearLayout.LayoutParams(0, ui.dp(heightDp) + 2 * gap, key.weight)
            row.addView(buildKey(key, toolbar), lp)
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, ui.dp(heightDp) + 2 * gap))
    }

    private fun buildKey(key: Key, toolbar: Boolean): KeyView {
        val isActionKey = !toolbar && key.label in ACTION_KEY_LABELS
        val keyStyle = key.style ?: when {
            key.label == "Apply" || isActionKey -> KeyStyle.ACCENT
            key.special -> KeyStyle.SPECIAL
            else -> KeyStyle.NORMAL
        }
        val round = key.style == KeyStyle.VOICE
        return KeyView(ime).apply {
            icon = key.icon
            iconSizeDp = if (round) 28f else 22f
            // Pure-icon keys draw no text; the space bar shows the brand name.
            text = when {
                key.icon != null -> ""
                key.label == "space" -> "AKZSA"
                else -> key.label
            }
            textSize = when {
                key.label == "space" -> 12f
                key.label.length >= 4 -> 12f
                else -> state.kbMetrics.keyLabelSp
            }
            bind(keyStyle, ui.palette(), cornerRadiusDp = if (round) 60 else if (toolbar) 12 else 10, shadow = key.style != KeyStyle.TOOL, insetDp = maxOf(1, state.kbMetrics.keyGapDp / 2))
            isHapticFeedbackEnabled = state.settings.hapticFeedback
            if (!toolbar) hintText = numberHintFor(key)
            contentDescription = if (round) "Voice input" else KeyLabels.describe(key.label)
            if (!toolbar) {
                key.caseBase?.let { letterKeys.add(this to it) }
                if (key.label == "⇧" || key.label == "⇪") shiftKeyView = this
            }
            if (key.label == "⌫") installBackspaceTouch(this, key) else installKeyTouch(this, key, toolbar)
        }
    }

    /** Delete once immediately, then repeat while the key stays pressed. */
    private fun installBackspaceTouch(view: KeyView, key: Key) {
        // Accessibility click is supported separately. During a touch gesture the delete already happened on
        // ACTION_DOWN, so the UP-click must not delete twice.
        view.setOnClickListener {
            if (!hub.typing.isBackspaceRepeating) key.action()
        }
        var downRawX = 0f
        var wordsDeleted = 0
        val wordStepPx = ui.dp(28).toFloat()
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    wordsDeleted = 0
                    touched.isPressed = true
                    if (state.settings.hapticFeedback) touched.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    playKeySound()
                    hub.typing.startBackspaceRepeat(touched)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val stillOnKey = touched.isPressed   // false if the finger already slid off (repeat was stopped)
                    touched.isPressed = false
                    // Announces the key to accessibility; the click listener skips the delete while repeating is active.
                    if (stillOnKey) touched.performClick()
                    hub.typing.stopBackspaceRepeat(touched)
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    touched.isPressed = false
                    hub.typing.stopBackspaceRepeat(touched)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val slopPx = KeyTouchPolicy.SLOP_DP * ime.resources.displayMetrics.density
                    val words = SwipeGestures.wordDeleteCount(event.rawX - downRawX, wordStepPx)
                    if (words > wordsDeleted) {
                        // Swipe left on backspace deletes whole words, one per step of travel.
                        hub.typing.stopBackspaceRepeat(touched)
                        touched.isPressed = false
                        repeat(words - wordsDeleted) { hub.typing.deleteWord() }
                        wordsDeleted = words
                    } else if (wordsDeleted == 0 && touched.isPressed && !KeyTouchPolicy.isInside(event.x, event.y, touched.width, touched.height, slopPx * 3)) {
                        touched.isPressed = false          // finger slid off: stop auto-repeat
                        hub.typing.stopBackspaceRepeat(touched)
                    }
                    true
                }
                else -> true
            }
        }
    }

    /**
     * Touch rules (see [KeyTouchPolicy]): plain character keys type on touch-down; every other key fires on release
     * only if the finger is still on it, so sliding off cancels. Keys with alternatives (accents, punctuation) show
     * them after a long press. A big preview of the character appears while a character key is held.
     * The pressed colour is animated by [KeyView.setPressed]; here we only toggle the pressed state.
     */
    private fun installKeyTouch(view: KeyView, key: Key, toolbar: Boolean) {
        val slopPx = KeyTouchPolicy.SLOP_DP * ime.resources.displayMetrics.density
        var inTouch = false
        var cancelled = false
        var firedOnDown = false
        var accentMode = false
        var downRawX = 0f
        var swiping = false
        var appliedSteps = 0
        val isSpace = !toolbar && key.label == "space"
        val stepPx = ui.dp(12).toFloat()
        val glideCapable = !toolbar && key.caseBase != null && state.settings.glideTyping && !state.passwordField
        var gliding = false
        var downRawY = 0f
        val glidePath = StringBuilder()
        val glideStartPx = ui.dp(22).toFloat()

        fun fire() {
            val base = key.caseBase
            if (base != null) hub.typing.typeText(shifted(base)) else key.action()
        }
        val openAccents = Runnable {
            if (cancelled || toolbar || !view.isPressed) return@Runnable
            val options = listOfNotNull(numberHintFor(key)) + KeyAccents.forLabel(view.text.toString())
            if (options.isEmpty()) return@Runnable
            accentMode = true
            popup.showAccents(view, options, ui.palette())
            if (state.settings.hapticFeedback) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        fun finish() {
            view.removeCallbacks(openAccents)
            popup.dismiss()
            view.isPressed = false
            inTouch = false
        }

        // Accessibility services (TalkBack double-tap) and hardware keys click the view without a touch gesture.
        view.setOnClickListener { if (!inTouch) fire() }
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    inTouch = true
                    cancelled = false
                    firedOnDown = false
                    accentMode = false
                    downRawX = event.rawX
                    downRawY = event.rawY
                    swiping = false
                    appliedSteps = 0
                    gliding = false
                    glidePath.setLength(0)
                    touched.isPressed = true
                    if (state.settings.hapticFeedback) touched.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    playKeySound()
                    val label = view.text.toString()
                    val hasAccents = !toolbar && (numberHintFor(key) != null || KeyAccents.forLabel(label).isNotEmpty())
                    if (!toolbar && KeyTouchPolicy.wantsPreview(label)) popup.showPreview(view, label, ui.palette())
                    if (key.commitOnDown && !hasAccents && !isSpace) {
                        firedOnDown = true
                        fire()
                    } else if (hasAccents) {
                        view.postDelayed(openAccents, KeyTouchPolicy.LONG_PRESS_MS)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (glideCapable && !accentMode &&
                        (gliding || kotlin.math.hypot(event.rawX - downRawX, event.rawY - downRawY) > glideStartPx)
                    ) {
                        // Glide typing: the finger left the key, so collect the letters it passes over.
                        if (!gliding) {
                            gliding = true
                            cancelled = true
                            view.removeCallbacks(openAccents)
                            popup.dismiss()
                            touched.isPressed = false
                            if (firedOnDown) hub.typing.cancelLastCharacter()
                            glidePath.setLength(0)
                            glidePath.append(key.caseBase.orEmpty())
                        }
                        letterAt(event.rawX, event.rawY)?.let { letter ->
                            if (glidePath.isEmpty() || glidePath.last().toString() != letter) glidePath.append(letter)
                        }
                    } else if (isSpace && !accentMode && (swiping || (!cancelled && kotlin.math.abs(event.rawX - downRawX) > stepPx))) {
                        // Space-bar swipe moves the cursor; the space itself is not typed.
                        if (!swiping) { swiping = true; cancelled = true; popup.dismiss(); touched.isPressed = false }
                        val steps = SwipeGestures.cursorSteps(event.rawX - downRawX, stepPx)
                        if (steps != appliedSteps) { hub.typing.moveCursor(steps - appliedSteps); appliedSteps = steps }
                    } else if (accentMode) {
                        popup.moveTo(event.rawX)
                    } else if (!cancelled && !firedOnDown &&
                        !KeyTouchPolicy.isInside(event.x, event.y, touched.width, touched.height, slopPx)
                    ) {
                        cancelled = true            // slid off the key: nothing will be typed
                        view.removeCallbacks(openAccents)
                        popup.dismiss()
                        touched.isPressed = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val choice = if (accentMode) popup.selectedOption() else null
                    val inside = KeyTouchPolicy.isInside(event.x, event.y, touched.width, touched.height, slopPx)
                    val shouldFire = !accentMode && !cancelled && !firedOnDown && inside
                    val glideText = if (gliding) glidePath.toString() else ""
                    finish()
                    when {
                        glideText.isNotEmpty() -> hub.typing.commitGlide(glideText)
                        choice != null -> hub.typing.typeText(choice)
                        shouldFire -> fire()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    finish()
                    true
                }
                else -> true
            }
        }
    }

    /** The letter key under a screen position, or null. Used by glide typing. */
    private fun letterAt(rawX: Float, rawY: Float): String? {
        val loc = IntArray(2)
        for ((view, base) in letterKeys) {
            view.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX <= loc[0] + view.width && rawY >= loc[1] && rawY <= loc[1] + view.height) return base
        }
        return null
    }

    private fun playKeySound() {
        if (!state.settings.keySound) return
        val audio = ime.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        audio.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD, state.settings.keySoundVolume.coerceIn(0, 100) / 100f)
    }

    /** The digit shown above (and typed by long-pressing) the letters Q to P, when number hints are on. */
    private fun numberHintFor(key: Key): String? {
        if (!state.settings.numberHints || state.settings.showNumberRow) return null
        val index = key.caseBase?.let { "qwertyuiop".indexOf(it) } ?: return null
        return if (index >= 0) "1234567890"[index].toString() else null
    }

    // ---- sizing ----

    private fun typingRowCount(): Int {
        val type = hub.editorInfo?.inputType ?: InputType.TYPE_CLASS_TEXT
        val cls = type and InputType.TYPE_MASK_CLASS
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        if (numeric) return 5
        // Letters / symbols: 3 key rows + the two bottom rows. Devanagari has one more.
        if (state.hindiLayout && !state.symbolsMode) return NativeLayouts.rows(state.nativeScript, 0).size + 2
        val numberRow = state.settings.showNumberRow && !state.symbolsMode
        return KeyboardLayoutPolicy.DEFAULT_TYPING_ROWS + if (numberRow) 1 else 0
    }

    /** All sizing comes from measured space (never device model). Re-run on every render/config change. */
    private fun recomputeLayout() {
        val c = ime.resources.configuration
        ClipboardHistory.configure(ime, state.settings.persistClipboard, state.settings.clipboardRetentionHours, state.settings.incognito)
        val oneHanded = state.settings.oneHandedMode == "left" || state.settings.oneHandedMode == "right"
        state.kbMetrics = KeyboardLayoutPolicy.metrics(
            widthDp = c.screenWidthDp.toFloat() * (if (oneHanded) 0.82f else 1f),
            heightDp = c.screenHeightDp.toFloat(),
            fontScale = c.fontScale,
            fileMode = false,
            typingRows = typingRowCount(),
            userHeightFraction = state.settings.keyboardHeightPercent / 100f,
        )
    }

    /** Bounded width (centred on tablets / landscape) plus system-bar and cutout insets. */
    private fun applyRootPadding(root: LinearLayout) {
        val screenWidthPx = (ime.resources.configuration.screenWidthDp * ime.resources.displayMetrics.density).toInt()
        val spare = maxOf(0, screenWidthPx - ui.dp(state.kbMetrics.contentWidthDp))
        val (extraLeft, extraRight) = when (state.settings.oneHandedMode) {
            "left" -> 0 to spare          // keyboard hugs the left edge
            "right" -> spare to 0
            else -> spare / 2 to spare / 2
        }
        val pad = ui.dp(state.kbMetrics.horizontalPaddingDp)
        KeyboardInsets.install(root, intArrayOf(pad + extraLeft, ui.dp(4), pad + extraRight, ui.dp(4)))
    }

    private companion object {
        val ACTION_KEY_LABELS = setOf("Send", "Go", "Search", "Done", "Next", "↵")
        const val STRIP_HEIGHT_DP = 40
        const val CHIP_ROW_DP = 36
        const val PREVIEW_CHARS = 140
        val PROBLEM_WORDS = Regex("(?i)(fail|error|denied|disabled|not |could ?n|no speech|required|off in|can't|unavailable)")
    }
}
