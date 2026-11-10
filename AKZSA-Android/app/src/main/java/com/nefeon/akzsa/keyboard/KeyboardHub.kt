package com.nefeon.akzsa.keyboard

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.nefeon.akzsa.diagnostics.DiagnosticStore
import com.nefeon.akzsa.security.SecureTokenStore
import com.nefeon.akzsa.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Wiring point for the keyboard. [AkzsaInputMethodService] owns one hub and forwards IME lifecycle calls to it;
 * every controller reaches the others through the hub instead of through the service.
 *
 * Controllers must not touch other controllers from their constructors (the hub is still being built then);
 * they may use them freely from any method.
 */
internal class KeyboardHub(
    val ime: InputMethodService,
    val scope: CoroutineScope,
    val settingsRepository: SettingsRepository,
    val tokenStore: SecureTokenStore,
    val diagnosticStore: DiagnosticStore,
) {
    val state = KeyboardState()
    val ui = KeyboardUi(ime, state)

    val renderer = KeyboardRenderer(this)
    val toolbar = ToolbarController(this)
    val typing = TypingController(this)
    val plans = PlanController(this)
    val dictation = DictationController(this)

    val inputConnection: InputConnection? get() = ime.currentInputConnection
    val editorInfo: EditorInfo? get() = ime.currentInputEditorInfo

    fun render() = renderer.render()
    fun setStatus(message: String) = renderer.setStatus(message)

    fun ensureInputConnection(): Boolean {
        if (ime.currentInputConnection == null) {
            setStatus("The text field is no longer available. Tap the field and try again.")
            return false
        }
        return true
    }
}
