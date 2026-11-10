package com.nefeon.akzsa.keyboard

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.nefeon.akzsa.diagnostics.CrashReporter
import com.nefeon.akzsa.diagnostics.DiagnosticStore
import com.nefeon.akzsa.security.SecureTokenStore
import com.nefeon.akzsa.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * System IME: a thin lifecycle class. All behaviour lives in the controllers wired together by [KeyboardHub]:
 *
 *  - [KeyboardRenderer]       view building (inflates `res/layout/keyboard_frame.xml`, builds rows with [KeyView])
 *  - [ToolbarController]      toolbar rows, MORE expand, language cycle, settings, panel toggles
 *  - [TypingController]       character input, Shift, Backspace repeat, Enter, dictation commit
 *  - [PlanController]         reviewable AI plans and verified field edits
 *  - [DictationController]    microphone, recognition, cloud recording, transcript routing
 *
 * Speech callbacks are session-based; partial recognition is shown but never inserted.
 */
class AkzsaInputMethodService : InputMethodService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var hub: KeyboardHub

    override fun onCreate() {
        super.onCreate()
        try {
            val settingsRepository = SettingsRepository(applicationContext)
            hub = KeyboardHub(
                ime = this,
                scope = serviceScope,
                settingsRepository = settingsRepository,
                tokenStore = SecureTokenStore(applicationContext),
                diagnosticStore = DiagnosticStore(applicationContext),
            )
            ClipboardHistory.start(applicationContext) { hub.state.passwordField }
            serviceScope.launch {
                try {
                    settingsRepository.settings.collect { latest ->
                        hub.state.settings = latest
                        hub.render()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    CrashReporter.recordHandled(applicationContext, "settings-collect", e)
                    hub.setStatus(getString(com.nefeon.akzsa.R.string.status_settings_load_failed))
                }
            }
        } catch (e: Exception) {
            // `hub` is a lateinit val the rest of the class depends on; if setup above failed,
            // the service cannot function regardless, so log for diagnosis and rethrow rather
            // than leave `hub` uninitialized and fail more confusingly later.
            CrashReporter.recordHandled(applicationContext, "onCreate", e)
            throw e
        }
    }

    override fun onCreateInputView(): View = hub.renderer.inflateFrame()

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val state = hub.state
        // One policy decides what is protected (passwords, PIN/OTP/CVV hints, incognito editors).
        state.sensitivity = SensitiveFieldPolicy.classify(EditorFieldInfo.from(attribute))
        state.passwordField = state.sensitivity != FieldSensitivity.NORMAL
        hub.dictation.onNewInput()
        // A plan belongs to the editor that produced it; never carry it into another field/app.
        hub.plans.clearPending()
        if (state.passwordField) {
            // Never start voice recognition or read/send editor contents for password inputs.
            hub.dictation.cancelRecognition(updateStatus = false)
            state.status = SensitiveFieldPolicy.statusMessage(state.sensitivity)
        } else {
            state.status = KeyboardState.IDLE_STATUS
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        try {
            ClipboardHistory.captureCurrent()
            hub.typing.updateAutoShift()
            hub.render()
        } catch (e: Exception) {
            // Recoverable: the keyboard view already exists from onCreateInputView, so log and
            // leave it showing whatever it last rendered rather than crashing the host app's
            // input field entirely.
            CrashReporter.recordHandled(applicationContext, "onStartInputView", e)
        }
    }

    /** Keeps the input strip and suggestion chips in step with the text and cursor in the host field. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        try {
            hub.renderer.refreshStrip()
        } catch (e: Exception) {
            CrashReporter.recordHandled(applicationContext, "onUpdateSelection", e)
        }
    }

    /** Rotation, fold/unfold, split-screen, font-scale and dark-mode changes re-evaluate the layout policy. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hub.render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Do not leave key repeats or microphone recording active if the keyboard is hidden/focus changes.
        hub.typing.stopBackspaceRepeat()
        hub.dictation.cancelRecognition(updateStatus = false)
        hub.toolbar.closeTransientPanels()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        hub.typing.stopBackspaceRepeat()
        hub.dictation.cancelRecognition(updateStatus = false, destroy = true)
        ClipboardHistory.stop()
        serviceScope.cancel()
        super.onDestroy()
    }
}
