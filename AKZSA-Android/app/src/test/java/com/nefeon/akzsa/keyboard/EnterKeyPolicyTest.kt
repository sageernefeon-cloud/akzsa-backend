package com.nefeon.akzsa.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class EnterKeyPolicyTest {

    // Regression test for the real bug: Snapchat, Instagram DMs, and Facebook Messenger all use
    // an auto-growing (multiline) message box that still declares IME_ACTION_SEND. The old
    // policy checked "is this multiline?" before looking at the declared action at all, so it
    // always inserted a newline here instead of sending — which is exactly what a user hitting
    // "no Send button, it just makes a new line" was seeing. An editor that declares a real
    // action (and hasn't opted out via IME_FLAG_NO_ENTER_ACTION) must win over the multiline flag.
    @Test fun multilineSendFieldLikeSnapchatOrInstagramPerformsSendNotNewline() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        assertEquals(
            EnterKeyPolicy.Behavior.PERFORM_EDITOR_ACTION,
            EnterKeyPolicy.decide(inputType, EditorInfo.IME_ACTION_SEND),
        )
        assertEquals("Send", EnterKeyPolicy.label(inputType, EditorInfo.IME_ACTION_SEND))
    }

    @Test fun multilineDoneFieldPerformsDoneNotNewline() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        assertEquals(
            EnterKeyPolicy.Behavior.PERFORM_EDITOR_ACTION,
            EnterKeyPolicy.decide(inputType, EditorInfo.IME_ACTION_DONE),
        )
    }

    // An editor can still explicitly ask for newline-on-Enter (e.g. a genuine multiline note
    // field with a separate Send button elsewhere) via IME_FLAG_NO_ENTER_ACTION. That opt-out
    // must still be honored even though an action is also declared.
    @Test fun multilineFieldWithNoEnterActionFlagStillGetsNewline() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val options = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertEquals(EnterKeyPolicy.Behavior.INSERT_NEWLINE, EnterKeyPolicy.decide(inputType, options))
        assertEquals("↵", EnterKeyPolicy.label(inputType, options))
    }

    // A genuinely plain multiline field with no declared action (a notes app, say) still just
    // gets a newline — nothing to send to.
    @Test fun plainMultilineFieldWithNoActionGetsNewline() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        assertEquals(
            EnterKeyPolicy.Behavior.INSERT_NEWLINE,
            EnterKeyPolicy.decide(inputType, EditorInfo.IME_ACTION_UNSPECIFIED),
        )
    }

    @Test fun singleLineSearchFieldPerformsSearchAction() {
        assertEquals(
            EnterKeyPolicy.Behavior.PERFORM_EDITOR_ACTION,
            EnterKeyPolicy.decide(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH),
        )
        assertEquals("Search", EnterKeyPolicy.label(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH))
    }

    @Test fun simpleSingleLineFieldWithNoActionSendsEnterToHost() {
        assertEquals(
            EnterKeyPolicy.Behavior.SEND_ENTER_KEY,
            EnterKeyPolicy.decide(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_UNSPECIFIED),
        )
        assertEquals("↵", EnterKeyPolicy.label(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_UNSPECIFIED))
    }
}
