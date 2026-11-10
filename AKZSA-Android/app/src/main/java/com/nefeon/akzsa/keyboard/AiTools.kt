package com.nefeon.akzsa.keyboard

/**
 * The AI text tools shown in the keyboard's "AI" row. Pure Kotlin (no Android types) so it is unit-tested.
 *
 * The [AiTask.wire] names and every `style` string must match the backend (`server/app/transform.py`, STYLES);
 * `server/tests/test_android_ai_tools_contract.py` reads this file and fails if they drift apart.
 */
internal enum class AiTask(val wire: String, val title: String) {
    REPLY("reply", "Reply"),
    REWRITE("rewrite", "Rewrite"),
    TRANSLATE("translate", "Translate"),
    STRUCTURE("structure", "Notes"),
}

/** One choice inside a tool's menu: [label] is the key text, [style]/[language] are what the backend receives. */
internal data class AiOption(val label: String, val style: String? = null, val language: String? = null)

internal object AiTools {
    const val MAX_PERSONA = 300

    private val rewrite = listOf(
        AiOption("Fix", style = "fix"),
        AiOption("Polite", style = "polite"),
        AiOption("Short", style = "short"),
        AiOption("Formal", style = "professional"),
        AiOption("Friendly", style = "friendly"),
        AiOption("Expand", style = "expand"),
    )
    private val reply = listOf(
        AiOption("Auto", style = "neutral"),
        AiOption("Polite", style = "polite"),
        AiOption("Short", style = "short"),
        AiOption("Formal", style = "professional"),
        AiOption("Friendly", style = "friendly"),
    )
    private val translate = listOf(
        AiOption("Hindi", language = "Hindi"),
        AiOption("Hinglish", language = "Hinglish"),
        AiOption("English", language = "English"),
        AiOption("Kannada", language = "Kannada"),
    )
    private val structure = listOf(
        AiOption("Email", style = "email"),
        AiOption("To-do", style = "todo"),
        AiOption("Notes", style = "notes"),
        AiOption("Summary", style = "summary"),
    )

    fun options(task: AiTask): List<AiOption> = when (task) {
        AiTask.REPLY -> reply
        AiTask.REWRITE -> rewrite
        AiTask.TRANSLATE -> translate
        AiTask.STRUCTURE -> structure
    }

    /** Short line for the status bar and the review preview, e.g. "Rewrite · Polite". */
    fun describe(task: AiTask, option: AiOption): String = "${task.title} · ${option.label}"

    /** Shown when the tool has nothing to work on. */
    fun emptyHint(task: AiTask): String = when (task) {
        AiTask.REPLY -> "Copy the message you want to answer first, then tap Reply. Type or say what you want to say in the field if you like."
        AiTask.REWRITE -> "Type or select some text first, then choose how to rewrite it."
        AiTask.TRANSLATE -> "Type or select some text first, then choose a language."
        AiTask.STRUCTURE -> "Say or type your notes first, then choose Email, To-do, Notes or Summary."
    }

    /** One line, trimmed, at most [MAX_PERSONA] characters; blank becomes null so nothing is sent. */
    fun cleanPersona(raw: String?): String? =
        raw.orEmpty().replace(Regex("\\s+"), " ").trim().take(MAX_PERSONA).takeIf { it.isNotEmpty() }
}
