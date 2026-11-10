package com.nefeon.akzsa.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.nefeon.akzsa.MainActivity
import com.nefeon.akzsa.commands.ActionValidator
import com.nefeon.akzsa.commands.PlanKind
import com.nefeon.akzsa.commands.ValidationResult
import com.nefeon.akzsa.network.ApiResult
import com.nefeon.akzsa.network.BackendClient
import com.nefeon.akzsa.network.CommandRequest
import com.nefeon.akzsa.security.InstallId
import com.nefeon.akzsa.voice.SegmentResult
import com.nefeon.akzsa.voice.StreamingDictationSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Everything voice: the mic key, on-device recognition, live (streaming) cloud dictation, one-shot cloud
 * recording, and routing the transcript through the AI intent classifier. Speech callbacks are session-based;
 * partial recognition is shown but never inserted. The `listening` and `cloudUploadInProgress` flags live in
 * [KeyboardState] because the renderer draws the mic key from them.
 *
 * This class only reorganises the code that used to live in the IME service. The audio capture classes in
 * `voice/` are untouched.
 */
internal class DictationController(private val hub: KeyboardHub) {
    private val state get() = hub.state

    private var resultHandledForSession = false
    private var sessionCounter = 0
    private var recognizer: SpeechRecognizer? = null
    private var cloudRecorder: MediaRecorder? = null
    private var cloudAudioFile: File? = null
    private var cloudRecordingActive = false
    private var cloudUploadJob: Job? = null
    private var cloudRecordingSession = 0
    private var streamingSession: StreamingDictationSession? = null
    private var lastPartial = ""

    /** A new editor gained focus: forget a half-heard phrase from the previous one. */
    fun onNewInput() { lastPartial = ""; state.lastDictationText = null }

    fun onMicrophoneTapped() {
        val ime = hub.ime
        if (state.pendingPlan != null) {
            hub.setStatus("Review or cancel the pending action before starting another session")
            return
        }
        if (state.passwordField) {
            hub.setStatus("Voice disabled for protected fields")
            return
        }
        if (state.cloudUploadInProgress) {
            hub.setStatus("Cloud transcription is processing · please wait")
            return
        }
        if (state.listening) {
            if (cloudRecordingActive) stopCloudRecording()
            else {
                try { recognizer?.stopListening() } catch (e: Exception) { KeyboardLog.w("DictationController", e); cancelRecognition(updateStatus = false) }
                hub.setStatus("Finishing speech recognition…")
            }
            return
        }
        if (ContextCompat.checkSelfPermission(ime, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            hub.setStatus("Grant microphone access in AKZSA first")
            try {
                ime.startActivity(Intent(ime, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_REQUEST_MIC_PERMISSION, true)
                })
            } catch (e: Exception) { KeyboardLog.w("DictationController", e);
                Toast.makeText(ime, "Open AKZSA and grant microphone permission, then return to your text field.", Toast.LENGTH_LONG).show()
            }
            return
        }
        // Cloud recording is also routed through the universal intent classifier before any text is inserted.
        if (state.settings.cloudDictationEnabled) {
            startCloudRecording()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ime)) {
            hub.setStatus("No speech recognition service is available")
            return
        }
        startRecognition()
    }

    // ---- live cloud dictation ----

    /**
     * Live cloud dictation: phrases are cut at pauses, transcribed and inserted while the user keeps talking.
     * NOTE: carried over unchanged from the old service, where nothing calls it (plain dictation goes through the
     * AI intent classifier instead, see [startCloudRecording]). Kept so enabling it later is a one-line change.
     */
    @Suppress("unused")
    private fun startStreamingDictation(): Boolean {
        if (hub.tokenStore.readToken().isNullOrBlank()) return false
        val settings = state.settings
        val session = sessionCounter + 1
        val language = settings.recognitionLanguage
        val dictionary = UserDictionary.promptTerms(settings.dictionaryTerms)
        val baseUrl = settings.backendBaseUrl
        val installId = InstallId.get(hub.ime.applicationContext)
        sessionCounter = session
        val streaming = StreamingDictationSession(
            scope = hub.scope,
            noiseReduction = settings.noiseReduction,
            transcribe = { wav, contextTail -> transcribeStreamedPhrase(wav, contextTail, baseUrl, language, dictionary, installId) },
            onText = { text -> if (session == sessionCounter && !state.passwordField && hub.inputConnection != null) hub.typing.commitDictation(text) },
            onStatus = { message -> if (session == sessionCounter) hub.setStatus(message) },
            onFinished = { reason -> if (session == sessionCounter) finishStreamingDictation(reason) },
        )
        if (!streaming.start()) {
            sessionCounter = session - 1
            return false
        }
        streamingSession = streaming
        cloudRecordingActive = true
        state.listening = true
        cloudRecordingSession = session
        resultHandledForSession = false
        hub.plans.clearPending()
        hub.render()
        return true
    }

    private suspend fun transcribeStreamedPhrase(
        wav: ByteArray,
        contextTail: String,
        baseUrl: String,
        language: String,
        dictionary: String,
        installId: String,
    ): SegmentResult {
        val token = hub.tokenStore.readTokenAsync()
        if (token.isNullOrBlank()) return SegmentResult.Failed("No testing token saved. Open AKZSA Settings.", retryable = false, fatal = true)
        return when (val response = BackendClient.transcribeSegment(baseUrl, token, wav, language, dictionary, contextTail, installId)) {
            is ApiResult.Success -> {
                hub.diagnosticStore.recordBackendSuccess(response.statusCode, response.latencyMs, "success")
                val text = response.value.text.trim().let { if (language == "hinglish") HinglishTransliterator.toRoman(it) else it }
                if (text.isBlank()) SegmentResult.Silent else SegmentResult.Text(text)
            }
            is ApiResult.Failure -> when (response.statusCode) {
                422 -> SegmentResult.Silent
                401, 403 -> SegmentResult.Failed(response.message, retryable = false, fatal = true)
                413, 415 -> SegmentResult.Failed(response.message, retryable = false)
                else -> SegmentResult.Failed(response.message, retryable = true)
            }
        }
    }

    private fun finishStreamingDictation(reason: String?) {
        streamingSession = null
        cloudRecordingActive = false
        state.listening = false
        state.cloudUploadInProgress = false
        hub.render()
        hub.setStatus(reason ?: "Cloud dictation finished")
    }

    // ---- one-shot cloud recording ----

    /** Records a user-started voice session locally, then uploads it only when the user taps Talk again. */
    private fun startCloudRecording() {
        val ime = hub.ime
        // Live mode for plain dictation; commands/writing keep the one-shot path. Falls back if the microphone cannot be opened.
        // Universal AI mode classifies each completed utterance before it is inserted.
        // Phrase-by-phrase streaming would commit text before intent classification, so it is
        // intentionally bypassed in this mode.
        val file = File(ime.cacheDir, "akzsa-dictation-${System.currentTimeMillis()}.m4a")
        var created: MediaRecorder? = null
        try {
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ime) else MediaRecorder()
            created = recorder
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(16_000)
            recorder.setAudioEncodingBitRate(64_000)
            recorder.setAudioChannels(1)
            recorder.setOutputFile(file.absolutePath)
            recorder.setMaxDuration(180_000)
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && cloudRecordingActive) {
                    hub.scope.launch { stopCloudRecording() }
                }
            }
            recorder.prepare()
            recorder.start()
            cloudRecorder = recorder
            cloudAudioFile = file
            cloudRecordingActive = true
            state.listening = true
            cloudRecordingSession = ++sessionCounter
            resultHandledForSession = false
            hub.plans.clearPending()
            hub.render()
            hub.setStatus("Recording · tap Stop to finish (max 3 minutes)")
        } catch (e: Exception) { KeyboardLog.w("DictationController", e);
            try { created?.release() } catch (e: Exception) { KeyboardLog.w("DictationController", e); }
            file.delete()
            cloudRecorder = null
            cloudAudioFile = null
            cloudRecordingActive = false
            state.listening = false
            hub.setStatus("Could not start cloud recording. Check microphone permission and device support.")
        }
    }

    private fun stopCloudRecording() {
        if (!cloudRecordingActive) return
        streamingSession?.let { streaming ->
            cloudRecordingActive = false
            state.listening = false
            state.cloudUploadInProgress = true   // keyboard shows "Processing" until the last phrase is inserted
            streaming.stop()
            hub.render()
            hub.setStatus("Finishing the last phrase...")
            return
        }
        val recorder = cloudRecorder
        val file = cloudAudioFile
        cloudRecorder = null
        cloudRecordingActive = false
        state.listening = false
        try {
            recorder?.stop()
        } catch (e: Exception) { KeyboardLog.w("DictationController", e);
            try { recorder?.release() } catch (e: Exception) { KeyboardLog.w("DictationController", e); }
            file?.delete()
            cloudAudioFile = null
            hub.setStatus("Recording was too short or could not be finalized. Please try again.")
            return
        } finally {
            try { recorder?.release() } catch (e: Exception) { KeyboardLog.w("DictationController", e); }
        }
        if (file == null || !file.exists() || file.length() < 512L) {
            file?.delete()
            cloudAudioFile = null
            hub.setStatus("No usable audio was recorded. Tap Talk and speak before stopping.")
            return
        }
        if (file.length() > 20L * 1024L * 1024L) {
            file.delete()
            cloudAudioFile = null
            hub.setStatus("Recording is too large. Please dictate a shorter session.")
            return
        }
        state.cloudUploadInProgress = true
        val session = cloudRecordingSession
        hub.render()
        hub.setStatus("Uploading audio · transcribing with Whisper…")
        cloudUploadJob = hub.scope.launch {
            try {
                val latest = hub.settingsRepository.settings.first()
                state.settings = latest
                if (session != sessionCounter || state.passwordField) return@launch
                val token = hub.tokenStore.readTokenAsync()
                if (token.isNullOrBlank()) {
                    hub.setStatus("No testing token saved. Audio was not uploaded; open AKZSA Settings.")
                    return@launch
                }
                // Do not polish dictation before intent classification: polishing can alter commands.
                val shouldPolish = false
                when (val response = BackendClient.transcribeAudio(
                    baseUrl = state.settings.backendBaseUrl,
                    bearerToken = token,
                    audioFile = file,
                    languageTag = state.settings.recognitionLanguage,
                    vocabulary = UserDictionary.promptTerms(state.settings.dictionaryTerms),
                    polish = shouldPolish,
                    installId = InstallId.get(hub.ime.applicationContext),
                )) {
                    is ApiResult.Failure -> {
                        if (session == sessionCounter) hub.setStatus("Cloud transcription failed: ${response.message}")
                    }
                    is ApiResult.Success -> {
                        if (session != sessionCounter || state.passwordField || hub.inputConnection == null) return@launch
                        val transcript = response.value.text.trim().let { if (state.settings.recognitionLanguage == "hinglish") HinglishTransliterator.toRoman(it) else it }
                        if (transcript.isBlank()) {
                            hub.setStatus("The speech service returned no text. Nothing was inserted.")
                        } else {
                            // All speech uses the same AI intent classifier: dictation, writing, or edit.
                            handleTranscript(transcript)
                        }
                        hub.diagnosticStore.recordBackendSuccess(response.statusCode, response.latencyMs, "success")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) { KeyboardLog.w("DictationController", e);
                if (session == sessionCounter) hub.setStatus("Cloud transcription failed. Check connection and AKZSA Settings.")
            } finally {
                file.delete()
                if (cloudAudioFile == file) cloudAudioFile = null
                if (session == sessionCounter) {
                    state.cloudUploadInProgress = false
                    cloudUploadJob = null
                    hub.render()
                }
            }
        }
    }

    // ---- on-device recognition ----

    private fun startRecognition() {
        val ime = hub.ime
        val session = ++sessionCounter
        resultHandledForSession = false
        lastPartial = ""
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(ime).also {
                it.setRecognitionListener(createRecognitionListener(session))
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Hinglish = Hindi speech recognised by the Hindi engine, then written in Roman letters (see handleTranscript).
                val deviceLanguage = if (state.settings.recognitionLanguage == "hinglish") "hi-IN" else state.settings.recognitionLanguage
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, deviceLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, deviceLanguage)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                // Best-effort hints (providers may ignore them): do not cut the user off at the first short pause.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ime.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Supported recognition services can return the formatted hypothesis first,
                    // with punctuation/capitalization; these hints are best-effort by provider.
                    putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
                    val biasTerms = (listOf("AKZSA", "NEFEON", "delete the last five words", "clear all text", "new paragraph") +
                        UserDictionary.biasTerms(state.settings.dictionaryTerms, 50))
                        .distinct().take(60)
                    putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(biasTerms))
                }
            }
            state.listening = true
            hub.plans.clearPending()
            hub.render()
            hub.setStatus(
                when {
                    state.settings.recognitionLanguage == "hinglish" -> "Listening · speak Hinglish; it is typed in Roman letters"
                    else -> "Listening · speak now"
                }
            )
            recognizer?.startListening(intent)
        } catch (_: SecurityException) {
            state.listening = false
            hub.setStatus("Microphone permission is required")
        } catch (e: Exception) { KeyboardLog.w("DictationController", e);
            state.listening = false
            hub.setStatus("Could not start speech recognition")
        }
    }

    private fun createRecognitionListener(session: Int) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { if (session == sessionCounter) hub.setStatus("Listening · speak now") }
        override fun onBeginningOfSpeech() { if (session == sessionCounter) hub.setStatus("Listening…") }
        override fun onRmsChanged(rmsdB: Float) { state.audioLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { if (session == sessionCounter) hub.setStatus("Processing speech…") }
        override fun onError(error: Int) {
            if (session != sessionCounter || resultHandledForSession) return
            resultHandledForSession = true
            state.listening = false
            hub.render()
            // Do not lose speech that was already recognised: recover the last partial hypothesis when the engine
            // ends with "no match", a timeout or a transient server/network error.
            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_SERVER || error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
            val recovered = lastPartial.trim()
            lastPartial = ""
            if (recoverable && recovered.isNotBlank()) {
                handleTranscript(recovered)
                hub.setStatus("Inserted what was heard before the speech service stopped")
                return
            }
            hub.setStatus(speechErrorMessage(error))
        }
        override fun onResults(results: Bundle?) {
            if (session != sessionCounter || resultHandledForSession) return
            resultHandledForSession = true
            state.listening = false
            hub.render()
            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            if (transcript.isBlank()) {
                hub.setStatus("No speech detected. Tap Talk to try again.")
                return
            }
            handleTranscript(transcript)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            if (session != sessionCounter || resultHandledForSession) return
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (partial.isNotBlank()) { lastPartial = partial; hub.setStatus("Listening · $partial") }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    // ---- transcript -> intent -> plan ----

    private fun handleTranscript(rawTranscript: String) {
        // Hinglish is typed in Roman letters even when the recogniser returns Devanagari.
        val transcript = if (state.settings.recognitionLanguage == "hinglish") HinglishTransliterator.toRoman(rawTranscript) else rawTranscript
        if (state.passwordField) {
            hub.setStatus("Voice and AI actions are disabled for protected fields")
            return
        }
        if (!hub.ensureInputConnection()) return
        // Spoken editing commands ("delete word", "new line", "heart emoji") run on-device, whole utterance only.
        if (state.settings.voiceEditCommands) {
            VoiceCommands.parse(transcript)?.let { cmd ->
                hub.typing.runVoiceCommand(cmd)
                hub.setStatus("Done")
                return
            }
            VoiceCommands.emojiFor(transcript)?.let { emoji ->
                hub.inputConnection?.commitText(emoji, 1)
                hub.setStatus("Emoji inserted")
                return
            }
        }
        // Snippets are checked first, entirely on-device: a whole-utterance match expands immediately
        // with no backend call (no network latency, no AI cost). Only a non-match falls through to the
        // AI classifier below.
        if (com.nefeon.akzsa.FeatureFlags.SNIPPETS_ENABLED) {
            val expansion = SnippetDictionary.match(transcript, state.settings.snippetTerms)
            if (expansion != null) {
                hub.typing.commitVerbatim(expansion)
                hub.setStatus("Snippet inserted")
                return
            }
        }
        // Single-mic universal flow: every utterance goes through AI intent classification.
        // The model chooses dictation, writing, or a supported editing command.
        hub.setStatus("Understanding your request…")
        hub.scope.launch {
            try {
                val latestSettings = hub.settingsRepository.settings.first()
                state.settings = latestSettings
                val settings = latestSettings
                val token = hub.tokenStore.readTokenAsync()
                if (token.isNullOrBlank()) {
                    hub.setStatus("No test token saved. Open AKZSA Settings to configure one.")
                    return@launch
                }
                // Read editor context only after the user enabled the explicit context-sharing setting.
                val ic = hub.inputConnection
                val selected = if (settings.shareEditorContext) ic?.getSelectedText(0)?.toString()?.take(10_000) else null
                val surrounding = if (settings.shareEditorContext) {
                    val before = hub.inputConnection?.getTextBeforeCursor(4_000, 0)?.toString().orEmpty()
                    val after = hub.inputConnection?.getTextAfterCursor(4_000, 0)?.toString().orEmpty()
                    (before + after).take(10_000).ifBlank { null }
                } else null
                val lastDictation = if (com.nefeon.akzsa.FeatureFlags.BACKTRACK_ENABLED) state.lastDictationText?.take(2_000) else null
                val payload = CommandRequest(
                    transcript = transcript.take(8_000),
                    selected_text = selected,
                    surrounding_text = surrounding,
                    mode = "auto",
                    last_dictation_text = lastDictation,
                )
                when (val response = BackendClient.interpret(settings.backendBaseUrl, token, payload, InstallId.get(hub.ime.applicationContext))) {
                    is ApiResult.Failure -> {
                        hub.diagnosticStore.recordBackendFailure(response.statusCode, response.latencyMs, if (response.statusCode == 401 || response.statusCode == 403) "failed" else null)
                        hub.setStatus(response.message)
                    }
                    is ApiResult.Success -> {
                        hub.diagnosticStore.recordBackendSuccess(response.statusCode, response.latencyMs, "success")
                        when (val validated = ActionValidator.validate(response.value.action, transcript, selected, lastDictation)) {
                            is ValidationResult.Rejected -> {
                                // Local-only troubleshooting: never log/send the transcript outside this device here.
                                val heard = transcript.replace("\r", " ").replace("\n", " ").take(140)
                                val detail = "AKZSA heard: \"$heard\"\nBackend action: ${response.value.action.kind}\nModel: ${response.value.model}\nResult: ${validated.message}"
                                Toast.makeText(hub.ime, detail, Toast.LENGTH_LONG).show()
                                hub.setStatus(if (response.value.action.kind == "unknown") "Not understood · heard: ${heard.take(44)}" else validated.message)
                            }
                            is ValidationResult.Accepted -> {
                                val serverAsked = response.value.action.requires_confirmation
                                val requireConfirmation = serverAsked || validated.plan.requiresConfirmation ||
                                    (settings.alwaysConfirmEdits && validated.plan.kind !in setOf(PlanKind.DICTATION))
                                val plan = validated.plan.copy(requiresConfirmation = requireConfirmation)
                                if (plan.requiresConfirmation) {
                                    hub.plans.propose(plan, "Review the proposed action")
                                } else {
                                    hub.plans.executePlan(plan)
                                }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) { KeyboardLog.w("DictationController", e);
                hub.setStatus("Could not process the command. Check AKZSA Settings and diagnostics.")
            }
        }
    }

    // ---- teardown ----

    fun cancelRecognition(updateStatus: Boolean, destroy: Boolean = false) {
        sessionCounter += 1
        resultHandledForSession = true
        state.listening = false
        streamingSession?.cancel()
        streamingSession = null
        if (cloudRecordingActive) {
            cloudRecordingActive = false
            try { cloudRecorder?.stop() } catch (e: Exception) { KeyboardLog.w("DictationController", e); }
            try { cloudRecorder?.release() } catch (e: Exception) { KeyboardLog.w("DictationController", e); }
            cloudRecorder = null
            cloudAudioFile?.delete()
            cloudAudioFile = null
        }
        if (state.cloudUploadInProgress) {
            cloudUploadJob?.cancel()
            cloudUploadJob = null
            state.cloudUploadInProgress = false
        }
        try { recognizer?.cancel() } catch (e: Exception) { KeyboardLog.w("DictationController", e); }
        if (destroy) {
            try { recognizer?.destroy() } catch (e: Exception) { KeyboardLog.w("DictationController", e); }
            recognizer = null
        }
        if (updateStatus) hub.setStatus("Listening stopped")
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Check the device microphone."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition was interrupted. Tap Talk to try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission was denied."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service network error. Check connectivity."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking more clearly."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Try again in a moment."
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error. Try again later."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap Talk to try again."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Speech service is rate-limiting requests. Wait a moment."
        // ERROR_LANGUAGE_NOT_SUPPORTED (12) / ERROR_LANGUAGE_UNAVAILABLE (13) exist from API 31.
        12, 13 -> "This phone's speech service can't use that language offline. Download it in Google speech settings or turn on Cloud Dictation in AKZSA."
        else -> "Speech recognition failed ($error). Try again."
    }
}
