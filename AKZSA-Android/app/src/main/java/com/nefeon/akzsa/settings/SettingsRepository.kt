package com.nefeon.akzsa.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val AccentNames = setOf("default", "blue", "green", "rose", "amber")

private val Context.akzsaDataStore by preferencesDataStore(name = "akzsa_settings")

data class AppSettings(
    val backendBaseUrl: String = "https://akzsa-server.onrender.com",
    val recognitionLanguage: String = "en-IN",
    /** Explicit opt-in: recorded audio is uploaded to the AKZSA server and forwarded to Groq Whisper. */
    val cloudDictationEnabled: Boolean = false,
    val dictionaryTerms: String = "AKZSA, NEFEON",
    /** Spoken snippets: "trigger = expansion" blocks separated by a line of "---". Expands locally, no AI call. */
    val snippetTerms: String = "",
    /** Live cloud dictation: phrases are sent and inserted while you keep talking. Off = record all, upload once. */
    val streamingDictation: Boolean = true,
    /** Rumble filter, the phone's noise suppressor and a quiet-speech boost for cloud dictation. */
    val noiseReduction: Boolean = true,
    /** "comma", "full stop", "new line"... are turned into symbols while dictating. */
    val spokenPunctuation: Boolean = true,
    val aiCommandModeDefault: Boolean = false,
    val appendSpaceAfterDictation: Boolean = true,
    val shareEditorContext: Boolean = false,
    val alwaysConfirmEdits: Boolean = true,
    val hapticFeedback: Boolean = true,
    val theme: String = "system",
    val onboardingComplete: Boolean = false,
    /** Optional writing style for AI Reply / Rewrite, e.g. "short, friendly, uses Hinglish". Sent only with those tools. */
    val aiPersona: String = "",
    /** Offline word completion, spelling candidates and next-word suggestions in the suggestion strip. */
    val smartSuggestions: Boolean = true,
    /** Fixes a short list of very common typos when space is pressed; backspace restores the typed word. */
    val autoCorrect: Boolean = true,
    /** Two quick spaces after a word insert ". ". */
    val doubleSpacePeriod: Boolean = true,
    /** Permanent 0-9 row above the letters. */
    val showNumberRow: Boolean = false,
    /** Small digits on Q-P (long-press types the digit). */
    val numberHints: Boolean = false,
    /** 85..120: scales the keyboard height within the safe limits of [KeyboardLayoutPolicy]. */
    val keyboardHeightPercent: Int = 100,
    /** "off", "left" or "right". */
    val oneHandedMode: String = "off",
    val keySound: Boolean = false,
    val keySoundVolume: Int = 50,
    /** Keep clipboard history on the device (never passwords, OTPs, PINs or card numbers). */
    val persistClipboard: Boolean = false,
    val clipboardRetentionHours: Int = 24,
    /** Nothing is remembered: no clipboard history, no recent emoji. */
    val incognito: Boolean = false,
    /** Swipe across letters to type a word. */
    val glideTyping: Boolean = true,
    /** Spoken editing commands such as "delete word" (whole utterance only). */
    val voiceEditCommands: Boolean = true,
    /** "default", "blue", "green", "rose" or "amber". */
    val accentTheme: String = "default",
    /** Comma-separated toolbar labels in the user's order; empty = original order. */
    val toolbarOrder: String = "",
) {
    val languageLabel: String get() = when (recognitionLanguage) {
        "hi-IN" -> "Hindi (India)"
        "kn-IN" -> "Kannada (India)"
        "ta-IN" -> "Tamil (India)"
        "te-IN" -> "Telugu (India)"
        "bn-IN" -> "Bengali (India)"
        "mr-IN" -> "Marathi (India)"
        "gu-IN" -> "Gujarati (India)"
        "ml-IN" -> "Malayalam (India)"
        "pa-IN" -> "Punjabi (India)"
        "ur-IN" -> "Urdu (India)"
        "ar-SA" -> "Arabic"
        "hinglish" -> "Hinglish (Latin script)"
        "en-IN" -> "English (India)"
        else -> "English (US)"
    }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val backendUrl = stringPreferencesKey("backend_base_url")
        val language = stringPreferencesKey("recognition_language")
        val cloudDictation = booleanPreferencesKey("cloud_dictation_enabled")
        val dictionaryTerms = stringPreferencesKey("dictation_dictionary_terms")
        val snippetTerms = stringPreferencesKey("dictation_snippet_terms")
        val streamingDictation = booleanPreferencesKey("streaming_dictation")
        val noiseReduction = booleanPreferencesKey("noise_reduction")
        val spokenPunctuation = booleanPreferencesKey("spoken_punctuation")
        val commandMode = booleanPreferencesKey("ai_command_mode_default")
        val appendSpace = booleanPreferencesKey("append_space_after_dictation")
        val shareContext = booleanPreferencesKey("share_editor_context")
        val confirmEdits = booleanPreferencesKey("always_confirm_edits")
        val haptics = booleanPreferencesKey("haptic_feedback")
        val theme = stringPreferencesKey("theme")
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val aiPersona = stringPreferencesKey("ai_persona")
        val smartSuggestions = booleanPreferencesKey("smart_suggestions")
        val autoCorrect = booleanPreferencesKey("auto_correct")
        val doubleSpacePeriod = booleanPreferencesKey("double_space_period")
        val numberRow = booleanPreferencesKey("show_number_row")
        val numberHints = booleanPreferencesKey("number_hints")
        val keyboardHeight = intPreferencesKey("keyboard_height_percent")
        val oneHanded = stringPreferencesKey("one_handed_mode")
        val keySound = booleanPreferencesKey("key_sound")
        val keySoundVolume = intPreferencesKey("key_sound_volume")
        val persistClipboard = booleanPreferencesKey("persist_clipboard")
        val clipboardRetention = intPreferencesKey("clipboard_retention_hours")
        val incognito = booleanPreferencesKey("incognito")
        val glideTyping = booleanPreferencesKey("glide_typing")
        val voiceEditCommands = booleanPreferencesKey("voice_edit_commands")
        val accentTheme = stringPreferencesKey("accent_theme")
        val toolbarOrder = stringPreferencesKey("toolbar_order")
    }

    val settings: Flow<AppSettings> = context.akzsaDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                backendBaseUrl = preferences[Keys.backendUrl] ?: AppSettings().backendBaseUrl,
                recognitionLanguage = preferences[Keys.language] ?: AppSettings().recognitionLanguage,
                cloudDictationEnabled = preferences[Keys.cloudDictation] ?: false,
                dictionaryTerms = preferences[Keys.dictionaryTerms] ?: AppSettings().dictionaryTerms,
                snippetTerms = preferences[Keys.snippetTerms] ?: AppSettings().snippetTerms,
                streamingDictation = preferences[Keys.streamingDictation] ?: true,
                noiseReduction = preferences[Keys.noiseReduction] ?: true,
                spokenPunctuation = preferences[Keys.spokenPunctuation] ?: true,
                aiCommandModeDefault = preferences[Keys.commandMode] ?: false,
                appendSpaceAfterDictation = preferences[Keys.appendSpace] ?: true,
                shareEditorContext = preferences[Keys.shareContext] ?: false,
                alwaysConfirmEdits = preferences[Keys.confirmEdits] ?: true,
                hapticFeedback = preferences[Keys.haptics] ?: true,
                theme = preferences[Keys.theme] ?: "system",
                onboardingComplete = preferences[Keys.onboarding] ?: false,
                aiPersona = preferences[Keys.aiPersona] ?: "",
                smartSuggestions = preferences[Keys.smartSuggestions] ?: true,
                autoCorrect = preferences[Keys.autoCorrect] ?: true,
                doubleSpacePeriod = preferences[Keys.doubleSpacePeriod] ?: true,
                showNumberRow = preferences[Keys.numberRow] ?: false,
                numberHints = preferences[Keys.numberHints] ?: false,
                keyboardHeightPercent = (preferences[Keys.keyboardHeight] ?: 100).coerceIn(85, 120),
                oneHandedMode = preferences[Keys.oneHanded]?.takeIf { it == "left" || it == "right" } ?: "off",
                keySound = preferences[Keys.keySound] ?: false,
                keySoundVolume = (preferences[Keys.keySoundVolume] ?: 50).coerceIn(0, 100),
                persistClipboard = preferences[Keys.persistClipboard] ?: false,
                clipboardRetentionHours = (preferences[Keys.clipboardRetention] ?: 24).coerceIn(1, 720),
                incognito = preferences[Keys.incognito] ?: false,
                glideTyping = preferences[Keys.glideTyping] ?: true,
                voiceEditCommands = preferences[Keys.voiceEditCommands] ?: true,
                accentTheme = preferences[Keys.accentTheme]?.takeIf { it in AccentNames } ?: "default",
                toolbarOrder = preferences[Keys.toolbarOrder].orEmpty().take(120),
            )
        }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.akzsaDataStore.edit { prefs ->
            val current = AppSettings(
                backendBaseUrl = prefs[Keys.backendUrl] ?: AppSettings().backendBaseUrl,
                recognitionLanguage = prefs[Keys.language] ?: AppSettings().recognitionLanguage,
                cloudDictationEnabled = prefs[Keys.cloudDictation] ?: false,
                dictionaryTerms = prefs[Keys.dictionaryTerms] ?: AppSettings().dictionaryTerms,
                snippetTerms = prefs[Keys.snippetTerms] ?: AppSettings().snippetTerms,
                streamingDictation = prefs[Keys.streamingDictation] ?: true,
                noiseReduction = prefs[Keys.noiseReduction] ?: true,
                spokenPunctuation = prefs[Keys.spokenPunctuation] ?: true,
                aiCommandModeDefault = prefs[Keys.commandMode] ?: false,
                appendSpaceAfterDictation = prefs[Keys.appendSpace] ?: true,
                shareEditorContext = prefs[Keys.shareContext] ?: false,
                alwaysConfirmEdits = prefs[Keys.confirmEdits] ?: true,
                hapticFeedback = prefs[Keys.haptics] ?: true,
                theme = prefs[Keys.theme] ?: "system",
                onboardingComplete = prefs[Keys.onboarding] ?: false,
                aiPersona = prefs[Keys.aiPersona] ?: "",
                smartSuggestions = prefs[Keys.smartSuggestions] ?: true,
                autoCorrect = prefs[Keys.autoCorrect] ?: true,
                doubleSpacePeriod = prefs[Keys.doubleSpacePeriod] ?: true,
                showNumberRow = prefs[Keys.numberRow] ?: false,
                numberHints = prefs[Keys.numberHints] ?: false,
                keyboardHeightPercent = (prefs[Keys.keyboardHeight] ?: 100).coerceIn(85, 120),
                oneHandedMode = prefs[Keys.oneHanded]?.takeIf { it == "left" || it == "right" } ?: "off",
                keySound = prefs[Keys.keySound] ?: false,
                keySoundVolume = (prefs[Keys.keySoundVolume] ?: 50).coerceIn(0, 100),
                persistClipboard = prefs[Keys.persistClipboard] ?: false,
                clipboardRetentionHours = (prefs[Keys.clipboardRetention] ?: 24).coerceIn(1, 720),
                incognito = prefs[Keys.incognito] ?: false,
                glideTyping = prefs[Keys.glideTyping] ?: true,
                voiceEditCommands = prefs[Keys.voiceEditCommands] ?: true,
                accentTheme = prefs[Keys.accentTheme]?.takeIf { it in AccentNames } ?: "default",
                toolbarOrder = prefs[Keys.toolbarOrder].orEmpty().take(120),
            )
            val next = transform(current)
            prefs[Keys.backendUrl] = next.backendBaseUrl
            prefs[Keys.language] = next.recognitionLanguage
            prefs[Keys.cloudDictation] = next.cloudDictationEnabled
            prefs[Keys.dictionaryTerms] = next.dictionaryTerms.take(4000)
            prefs[Keys.snippetTerms] = next.snippetTerms.take(8000)
            prefs[Keys.streamingDictation] = next.streamingDictation
            prefs[Keys.noiseReduction] = next.noiseReduction
            prefs[Keys.spokenPunctuation] = next.spokenPunctuation
            prefs[Keys.commandMode] = next.aiCommandModeDefault
            prefs[Keys.appendSpace] = next.appendSpaceAfterDictation
            prefs[Keys.shareContext] = next.shareEditorContext
            prefs[Keys.confirmEdits] = next.alwaysConfirmEdits
            prefs[Keys.haptics] = next.hapticFeedback
            prefs[Keys.theme] = next.theme
            prefs[Keys.onboarding] = next.onboardingComplete
            prefs[Keys.aiPersona] = next.aiPersona.take(300)
            prefs[Keys.smartSuggestions] = next.smartSuggestions
            prefs[Keys.autoCorrect] = next.autoCorrect
            prefs[Keys.doubleSpacePeriod] = next.doubleSpacePeriod
            prefs[Keys.numberRow] = next.showNumberRow
            prefs[Keys.numberHints] = next.numberHints
            prefs[Keys.keyboardHeight] = next.keyboardHeightPercent.coerceIn(85, 120)
            prefs[Keys.oneHanded] = if (next.oneHandedMode == "left" || next.oneHandedMode == "right") next.oneHandedMode else "off"
            prefs[Keys.keySound] = next.keySound
            prefs[Keys.keySoundVolume] = next.keySoundVolume.coerceIn(0, 100)
            prefs[Keys.persistClipboard] = next.persistClipboard
            prefs[Keys.clipboardRetention] = next.clipboardRetentionHours.coerceIn(1, 720)
            prefs[Keys.incognito] = next.incognito
            prefs[Keys.glideTyping] = next.glideTyping
            prefs[Keys.voiceEditCommands] = next.voiceEditCommands
            prefs[Keys.accentTheme] = if (next.accentTheme in AccentNames) next.accentTheme else "default"
            prefs[Keys.toolbarOrder] = next.toolbarOrder.take(120)
        }
    }

    suspend fun reset() {
        context.akzsaDataStore.edit { it.clear() }
    }
}
