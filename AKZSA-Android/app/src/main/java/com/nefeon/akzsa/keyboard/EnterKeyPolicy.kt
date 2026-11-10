package com.nefeon.akzsa.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Centralizes Enter-key behavior. [decide] and [label] share one precedence so the key's
 * visible label and what it actually does can never drift apart again.
 *
 * The previous version checked "is this field multiline?" before looking at the editor's
 * declared action at all, so any app whose message box auto-grows (Snapchat, Instagram DMs,
 * Facebook Messenger, WhatsApp, Telegram all do this) got treated as "just insert a newline" —
 * even though the editor had explicitly set imeOptions to IME_ACTION_SEND. That is the bug
 * behind "no Send button, it just goes to a new line" in those apps.
 *
 * The correct precedence (and what Android's own EditorInfo contract says a well-behaved IME
 * should do): if the editor declared a real action (Send/Go/Search/Next/Done/Previous) and did
 * NOT set IME_FLAG_NO_ENTER_ACTION, Enter performs that action — regardless of the multiline
 * flag. Only when there is no such action, or the editor explicitly asked for newline-on-Enter
 * via that flag, do we fall back to "insert a newline if multiline, else send a raw Enter key".
 */
internal object EnterKeyPolicy {
    enum class Behavior { INSERT_NEWLINE, PERFORM_EDITOR_ACTION, SEND_ENTER_KEY }

    private val EXPLICIT_ACTIONS = setOf(
        EditorInfo.IME_ACTION_GO,
        EditorInfo.IME_ACTION_SEARCH,
        EditorInfo.IME_ACTION_SEND,
        EditorInfo.IME_ACTION_NEXT,
        EditorInfo.IME_ACTION_DONE,
        EditorInfo.IME_ACTION_PREVIOUS,
    )

    fun decide(inputType: Int, imeOptions: Int): Behavior {
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (action in EXPLICIT_ACTIONS && !noEnterAction) return Behavior.PERFORM_EDITOR_ACTION

        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val isMultilineText = inputClass == InputType.TYPE_CLASS_TEXT &&
            (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        return if (isMultilineText) Behavior.INSERT_NEWLINE else Behavior.SEND_ENTER_KEY
    }

    /** The key's visible label, using the exact same precedence as [decide] so they can't disagree. */
    fun label(inputType: Int, imeOptions: Int): String {
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (action in EXPLICIT_ACTIONS && !noEnterAction) {
            return when (action) {
                EditorInfo.IME_ACTION_GO -> "Go"
                EditorInfo.IME_ACTION_SEARCH -> "Search"
                EditorInfo.IME_ACTION_SEND -> "Send"
                EditorInfo.IME_ACTION_NEXT -> "Next"
                EditorInfo.IME_ACTION_DONE -> "Done"
                else -> "Prev" // IME_ACTION_PREVIOUS
            }
        }
        return "↵"
    }
}
