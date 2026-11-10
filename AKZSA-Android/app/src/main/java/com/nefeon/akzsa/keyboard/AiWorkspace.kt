package com.nefeon.akzsa.keyboard

/** Tabs of the AI Workspace panel. Grammar Fix and the AI Emoji Adder are separate tabs; the rest map to [AiTask]. */
internal enum class AiTab(val title: String, val task: AiTask?) {
    GRAMMAR("Grammar", null),
    EMOJI("AI Emoji", null),
    REPLY("Reply", AiTask.REPLY),
    REWRITE("Rewrite", AiTask.REWRITE),
    TRANSLATE("Translate", AiTask.TRANSLATE),
    NOTES("Notes", AiTask.STRUCTURE),
}

/** The two whole-field tools that are not plain [AiTask]s. */
internal object AiWorkspace {
    /** Grammar Fix uses the backend's "fix" rewrite style: grammar, spelling and punctuation only. */
    val GRAMMAR_OPTION = AiOption("Fix", style = "fix")

    const val EMOJI_INSTRUCTION =
        "Add a small number of contextually appropriate emojis to this text. Preserve all wording and meaning; return only the updated text."

    const val GRAMMAR_HINT = "Fixes grammar, spelling and punctuation on the selection, or on the whole field. You review the result before anything changes."
    const val EMOJI_HINT = "Suggests a version of your sentence with fitting emojis. Tap Regenerate for another take."
}
