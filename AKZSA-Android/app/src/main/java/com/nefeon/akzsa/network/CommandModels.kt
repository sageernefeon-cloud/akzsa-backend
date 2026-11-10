package com.nefeon.akzsa.network

import kotlinx.serialization.Serializable

@Serializable
data class CommandRequest(
    val transcript: String,
    val selected_text: String? = null,
    val surrounding_text: String? = null,
    val mode: String = "auto",
    /** The exact text AKZSA itself last dictated, so the backend can recognize a Backtrack self-correction. */
    val last_dictation_text: String? = null,
)

@Serializable
data class CommandAction(
    val kind: String,
    val count: Int? = null,
    val text: String? = null,
    val query: String? = null,
    val language: String? = null,
    val requires_confirmation: Boolean = false,
    val explanation: String = "",
)

@Serializable
data class CommandResponse(
    val action: CommandAction,
    val model: String = "",
    val server_executed_action: Boolean = false,
    val message: String = "",
)

@Serializable
data class TransformRequest(
    /** "rewrite", "reply", "translate" or "structure" (see server/app/transform.py). */
    val task: String,
    val text: String,
    val style: String? = null,
    val language: String? = null,
    val context: String? = null,
    val instruction: String? = null,
    val persona: String? = null,
)

@Serializable
data class TransformResponse(
    val task: String = "",
    val style: String = "",
    val text: String = "",
    val model: String = "",
    val server_executed_action: Boolean = false,
)

@Serializable
data class GifSearchItem(
    val url: String = "",
    val preview_url: String = "",
    val title: String = "",
)

@Serializable
data class GifSearchResponse(
    val results: List<GifSearchItem> = emptyList(),
)

@Serializable
data class HealthResponse(
    val status: String = "unknown",
    val service: String = "",
    val version: String = "",
    val groq_configured: Boolean? = null,
    val authentication_configured: Boolean? = null,
    val voice_transcription_configured: Boolean? = null,
)


@Serializable
data class VoiceTranscriptionResponse(
    val transcript: String = "",
    val text: String = "",
    val model: String = "",
    val polish_model: String? = null,
    val polish_applied: Boolean = false,
    val message: String = "",
)
