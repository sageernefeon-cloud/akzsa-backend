package com.nefeon.akzsa.keyboard

/**
 * Spoken snippets (Settings > "Snippets"). A short trigger phrase expands to a saved block of text —
 * entirely on-device, no backend call, no AI cost. Format, one snippet per block, blocks separated by
 * a line containing only "---":
 *
 *   my signature = Best regards,
 *   Jordan Lee
 *   +1 555 0100
 *   ---
 *   my address = 221B Baker Street, Springfield
 *
 * Matching is whole-utterance and ignores case, leading/trailing whitespace, and a trailing full stop
 * (so "my signature" and "My signature." both match). The trigger must match the ENTIRE spoken phrase,
 * not just appear inside it — this keeps normal dictation that happens to contain a trigger word from
 * being silently swallowed.
 */
object SnippetDictionary {
    const val MAX_ENTRIES = 100
    private const val MAX_TRIGGER = 60
    private const val MAX_EXPANSION = 2000

    data class Entry(val trigger: String, val expansion: String)

    fun parse(raw: String): List<Entry> {
        val entries = ArrayList<Entry>()
        for (block in raw.split(Regex("(?m)^\\s*---\\s*$"))) {
            val trimmedBlock = block.trim('\n', '\r', ' ')
            if (trimmedBlock.isEmpty()) continue
            val eqIndex = trimmedBlock.indexOfFirst { it == '=' }
            if (eqIndex <= 0) continue
            val trigger = trimmedBlock.substring(0, eqIndex).trim()
            val expansion = trimmedBlock.substring(eqIndex + 1).trimStart(' ').trimEnd('\n', '\r', ' ')
            if (trigger.isEmpty() || expansion.isEmpty()) continue
            if (trigger.length > MAX_TRIGGER || expansion.length > MAX_EXPANSION) continue
            entries += Entry(trigger, expansion)
            if (entries.size >= MAX_ENTRIES) break
        }
        return entries
    }

    private fun normalize(s: String): String =
        s.trim().trimEnd('.', '!', '?').trim().lowercase()

    /** Returns the expansion for an exact (normalized) whole-utterance match, or null if nothing matches. */
    fun match(transcript: String, raw: String): String? {
        if (transcript.isBlank() || raw.isBlank()) return null
        val needle = normalize(transcript)
        if (needle.isEmpty()) return null
        for (entry in parse(raw)) {
            if (normalize(entry.trigger) == needle) return entry.expansion
        }
        return null
    }
}
