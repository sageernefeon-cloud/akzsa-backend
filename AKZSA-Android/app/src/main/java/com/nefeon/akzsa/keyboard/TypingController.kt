package com.nefeon.akzsa.keyboard

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Key actions that edit the host field: character input, Shift / Caps, Backspace (with auto-repeat), Enter,
 * dictation commit, auto-capitalisation and keyboard switching. GIF and File AI modes redirect typing into
 * their own query boxes exactly as before.
 */
internal class TypingController(private val hub: KeyboardHub) {
    private val state get() = hub.state
    private val keyRepeatHandler = Handler(Looper.getMainLooper())
    private var repeatingBackspaceView: View? = null
    private var backspaceRepeatTask: Runnable? = null

    val isBackspaceRepeating: Boolean get() = repeatingBackspaceView != null

    fun typeText(value: String) {
        if (state.gifPanelOpen && !state.passwordField) {
            hub.toolbar.gifController.append(value)
            return
        }
        if (state.emojiPanelOpen && state.emojiSearching && !state.passwordField) {
            state.emojiQuery = (state.emojiQuery + value).take(30)
            hub.render()
            return
        }
        if (state.clipboardRowOpen && state.clipSearching && !state.passwordField) {
            state.clipQuery = (state.clipQuery + value).take(40)
            hub.render()
            return
        }
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        pendingUndo = null
        if (value == " " && !state.passwordField && !state.hindiLayout && handleSpace(ic)) {
            refreshShiftAfterEdit()
            return
        }
        ic.commitText(value, 1)
        refreshShiftAfterEdit()
    }

    private var lastSpaceMs = 0L
    /** (typed word, correction) of the last automatic correction; backspace right after it restores the typed word. */
    private var pendingUndo: Pair<String, String>? = null

    /** Double-space full stop and conservative auto-correction. Returns true when it already inserted the space. */
    private fun handleSpace(ic: InputConnection): Boolean {
        val now = SystemClock.uptimeMillis()
        val sinceLast = now - lastSpaceMs
        lastSpaceMs = now
        if (!ic.getSelectedText(0).isNullOrEmpty()) return false
        val settings = state.settings
        val before = ic.getTextBeforeCursor(40, 0)
        if (settings.doubleSpacePeriod && TypingRules.shouldDoubleSpacePeriod(before, sinceLast)) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            lastSpaceMs = 0L
            return true
        }
        if (settings.autoCorrect && settings.recognitionLanguage.startsWith("en")) {
            val word = Suggestions.currentWord(before)
            val userWords = UserDictionary.parse(settings.dictionaryTerms).map { it.written.lowercase() }.toSet()
            val fix = AutoCorrect.fix(word, userWords) ?: return false
            ic.beginBatchEdit()
            ic.deleteSurroundingText(word.length, 0)
            ic.commitText("$fix ", 1)
            ic.endBatchEdit()
            pendingUndo = word to fix
            return true
        }
        return false
    }

    fun onShiftTapped() {
        val now = SystemClock.uptimeMillis()
        when {
            state.capsLock -> { state.capsLock = false; state.shiftEnabled = false }
            now - state.lastShiftTapMs < 350L -> { state.capsLock = true; state.shiftEnabled = true }   // double-tap = caps lock
            else -> state.shiftEnabled = !state.shiftEnabled
        }
        state.lastShiftTapMs = now
        hub.renderer.refreshShiftState()   // relabel keys in place; a full render() here made the keyboard lag
    }

    /** One-shot Shift is consumed after a character; sentence-start fields turn it on again automatically. */
    fun refreshShiftAfterEdit() {
        if (state.capsLock) return
        val before = state.shiftEnabled
        state.shiftEnabled = false
        updateAutoShift()
        if (state.shiftEnabled != before) hub.renderer.refreshShiftState()
    }

    fun updateAutoShift() {
        if (state.capsLock || state.passwordField || state.hindiLayout) return
        val ic = hub.inputConnection ?: return
        val info = hub.editorInfo ?: return
        if ((info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) { state.shiftEnabled = false; return }
        state.shiftEnabled = DictationFormatter.wantsCapital(ic.getCursorCapsMode(info.inputType))
    }

    /** Always offer a way out to the previous/next keyboard so AKZSA can never trap the user. */
    fun canSwitchKeyboard(): Boolean {
        val imm = hub.ime.getSystemService(InputMethodManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val token = hub.ime.window?.window?.attributes?.token ?: return true
            return imm.shouldOfferSwitchingToNextInputMethod(token)
        }
        return true
    }

    fun switchKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hub.ime.switchToNextInputMethod(false)) return
        hub.ime.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    fun commitDictation(spokenText: String) {
        val settings = state.settings
        // Local clean-up first: spoken punctuation ("comma", "new line") and the user's personal dictionary.
        val value = TranscriptCleaner.clean(spokenText.trim(), settings.dictionaryTerms, settings.spokenPunctuation)
        val ic = hub.inputConnection ?: run { hub.setStatus("The text field is no longer available"); return }
        val inputType = hub.editorInfo?.inputType ?: InputType.TYPE_CLASS_TEXT
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val safeToAppendSpace = inputClass == InputType.TYPE_CLASS_TEXT && variation !in setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            InputType.TYPE_TEXT_VARIATION_URI,
        )
        val text = DictationFormatter.format(
            transcript = value,
            textBeforeCursor = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty(),   // local only, never stored or sent
            hasSelection = !ic.getSelectedText(0).isNullOrEmpty(),
            spaceSafe = safeToAppendSpace,
            appendTrailingSpace = settings.appendSpaceAfterDictation,
            capitalizeFirst = DictationFormatter.wantsCapital(ic.getCursorCapsMode(inputType)),
        )
        if (text.isEmpty()) return
        ic.commitText(text, 1)
        // Backtrack needs the text exactly as it now sits in the field (capitalised, with its trailing space),
        // not the raw transcript, otherwise the "field still ends with what I dictated" check never matches.
        state.lastDictationText = text
        refreshShiftAfterEdit()
    }

    /** Inserts text exactly as given (used by Snippets: no spoken-punctuation, dictionary or auto-capital changes). */
    fun commitVerbatim(text: String) {
        val ic = hub.inputConnection ?: run { hub.setStatus("The text field is no longer available"); return }
        if (text.isEmpty()) return
        ic.commitText(text, 1)
        state.lastDictationText = text
        refreshShiftAfterEdit()
    }

    // ---- backspace ----

    fun startBackspaceRepeat(view: View) {
        stopBackspaceRepeat()
        repeatingBackspaceView = view
        backspace()
        val task = object : Runnable {
            override fun run() {
                if (repeatingBackspaceView !== view || !view.isPressed) {
                    stopBackspaceRepeat(view)
                    return
                }
                backspace()
                // Around 20 deletions per second after the long-press delay feels responsive
                // without flooding slower InputConnection implementations.
                keyRepeatHandler.postDelayed(this, 50L)
            }
        }
        backspaceRepeatTask = task
        keyRepeatHandler.postDelayed(task, 360L)
    }

    fun stopBackspaceRepeat(view: View? = null) {
        if (view != null && repeatingBackspaceView !== view) return
        backspaceRepeatTask?.let(keyRepeatHandler::removeCallbacks)
        backspaceRepeatTask = null
        repeatingBackspaceView = null
    }

    fun backspace() {
        if (state.gifPanelOpen && !state.passwordField) {
            hub.toolbar.gifController.backspace()
            return
        }
        if (state.emojiPanelOpen && state.emojiSearching && !state.passwordField) {
            state.emojiQuery = state.emojiQuery.dropLast(1)
            hub.render()
            return
        }
        if (state.clipboardRowOpen && state.clipSearching && !state.passwordField) {
            state.clipQuery = state.clipQuery.dropLast(1)
            hub.render()
            return
        }
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        if (selected.isNotEmpty()) {
            pendingUndo = null
            ic.commitText("", 1)
            return
        }
        pendingUndo?.let { (typed, fixed) ->
            pendingUndo = null
            if (ic.getTextBeforeCursor(fixed.length + 1, 0)?.toString() == "$fixed ") {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(fixed.length + 1, 0)
                ic.commitText(typed, 1)
                ic.endBatchEdit()
                return
            }
        }
        // Delete one Unicode code point (emoji/supplementary symbols included) when supported.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ic.deleteSurroundingTextInCodePoints(1, 0)) { refreshShiftAfterEdit(); return }
        val before = ic.getTextBeforeCursor(16, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        val units = Character.charCount(Character.codePointBefore(before, before.length))
        ic.deleteSurroundingText(units, 0)
        refreshShiftAfterEdit()
    }

    // ---- cursor and selection editing (all through InputConnection) ----

    /** Moves the cursor [delta] characters (negative = left) with real DPAD key events, so any editor handles it. */
    fun moveCursor(delta: Int) {
        if (delta == 0 || !hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        val code = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        pendingUndo = null
        ic.beginBatchEdit()
        repeat(minOf(kotlin.math.abs(delta), MAX_CURSOR_STEPS_PER_EVENT)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        ic.endBatchEdit()
    }

    /** Deletes the previous word (or the selection). */
    fun deleteWord() {
        if (state.gifPanelOpen && !state.passwordField) { hub.toolbar.gifController.backspace(); return }
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        pendingUndo = null
        if (!ic.getSelectedText(0).isNullOrEmpty()) { ic.commitText("", 1); return }
        val len = WordEdit.deleteWordLength(ic.getTextBeforeCursor(100, 0))
        if (len > 0) ic.deleteSurroundingText(len, 0)
        refreshShiftAfterEdit()
    }

    enum class SelectUnit { WORD, SENTENCE, PARAGRAPH }

    /** Selects the word or sentence around the cursor. Returns false when the editor does not expose its text. */
    fun selectAroundCursor(unit: SelectUnit): Boolean {
        if (!hub.ensureInputConnection()) return false
        val ic = hub.inputConnection ?: return false
        val before = ic.getTextBeforeCursor(300, 0)?.toString() ?: return false
        val after = ic.getTextAfterCursor(300, 0)?.toString().orEmpty()
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val range = when (unit) {
            SelectUnit.WORD -> WordEdit.wordRange(before + after, before.length)
            SelectUnit.SENTENCE -> WordEdit.sentenceRange(before + after, before.length)
            SelectUnit.PARAGRAPH -> WordEdit.paragraphRange(before + after, before.length)
        } ?: return false
        val cursorAbs = extracted.startOffset + extracted.selectionStart
        val origin = cursorAbs - before.length
        return ic.setSelection(origin + range.first, origin + range.second)
    }

    /** Deletes the previous sentence (or the selection). */
    fun deleteSentence() {
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        pendingUndo = null
        if (!ic.getSelectedText(0).isNullOrEmpty()) { ic.commitText("", 1); return }
        val len = WordEdit.deleteSentenceLength(ic.getTextBeforeCursor(400, 0))
        if (len > 0) ic.deleteSurroundingText(len, 0)
        refreshShiftAfterEdit()
    }

    /** Extends the selection one character left or right (Shift + arrow). */
    fun extendSelection(delta: Int) {
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        val code = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.META_SHIFT_ON))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, KeyEvent.META_SHIFT_ON))
    }

    /** Pastes [text] cleaned up (no trailing blanks, space after sentence punctuation). */
    fun pasteSmart(text: String) {
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        pendingUndo = null
        ic.commitText(SmartPaste.prepare(ic.getTextBeforeCursor(1, 0), text), 1)
        refreshShiftAfterEdit()
    }

    /** Commits the best word for a glide path, adding a space before it when it follows a word directly. */
    fun commitGlide(path: String) {
        if (state.passwordField || !hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        val before = ic.getTextBeforeCursor(60, 0)
        val word = GlideEngine.decode(path, Suggestions.previousWord(before)).firstOrNull() ?: return
        val cased = when {
            state.capsLock -> word.uppercase()
            state.shiftEnabled -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
        val lead = if (before?.lastOrNull()?.isLetterOrDigit() == true) " " else ""
        pendingUndo = null
        ic.commitText("$lead$cased ", 1)
        refreshShiftAfterEdit()
    }

    /** Removes the single character that a key typed on touch-down before the touch turned into a glide. */
    fun cancelLastCharacter() {
        if (!hub.ensureInputConnection()) return
        hub.inputConnection?.deleteSurroundingText(1, 0)
    }

    fun runVoiceCommand(cmd: VoiceCommands.Cmd) {
        val ic = hub.inputConnection ?: return
        when (cmd) {
            VoiceCommands.Cmd.DELETE_WORD -> deleteWord()
            VoiceCommands.Cmd.DELETE_SENTENCE -> deleteSentence()
            VoiceCommands.Cmd.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            VoiceCommands.Cmd.SELECT_WORD -> selectAroundCursor(SelectUnit.WORD)
            VoiceCommands.Cmd.SELECT_SENTENCE -> selectAroundCursor(SelectUnit.SENTENCE)
            VoiceCommands.Cmd.NEW_LINE -> ic.commitText("\n", 1)
            VoiceCommands.Cmd.NEW_PARAGRAPH -> ic.commitText("\n\n", 1)
            VoiceCommands.Cmd.UNDO -> undoRedo(redo = false)
            VoiceCommands.Cmd.REDO -> undoRedo(redo = true)
        }
    }

    /** Undo / redo as Ctrl+Z and Ctrl+Shift+Z key events (works where the editor implements them). */
    fun undoRedo(redo: Boolean) {
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        val meta = KeyEvent.META_CTRL_ON or if (redo) KeyEvent.META_SHIFT_ON else 0
        pendingUndo = null
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta))
    }

    // ---- enter ----

    fun pressEnter() {
        if (state.gifPanelOpen && !state.passwordField) {
            hub.toolbar.gifController.search()
            return
        }
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        val info = hub.editorInfo
        val inputType = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        val imeOptions = info?.imeOptions ?: EditorInfo.IME_ACTION_UNSPECIFIED
        when (EnterKeyPolicy.decide(inputType, imeOptions)) {
            EnterKeyPolicy.Behavior.INSERT_NEWLINE -> {
                ic.commitText("\n", 1)
            }
            EnterKeyPolicy.Behavior.PERFORM_EDITOR_ACTION -> {
                val action = imeOptions and EditorInfo.IME_MASK_ACTION
                if (!ic.performEditorAction(action)) sendEnterKeyEvents(ic)
            }
            EnterKeyPolicy.Behavior.SEND_ENTER_KEY -> {
                // Single-line fields should not receive an inserted newline. Send Enter to the host.
                sendEnterKeyEvents(ic)
            }
        }
    }

    private fun sendEnterKeyEvents(ic: InputConnection) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private companion object {
        const val MAX_CURSOR_STEPS_PER_EVENT = 60
    }
}
