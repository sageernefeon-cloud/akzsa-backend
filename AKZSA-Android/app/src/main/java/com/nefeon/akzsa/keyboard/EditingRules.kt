package com.nefeon.akzsa.keyboard

/** Pure text-editing and productivity rules (unit-tested, no Android types). */
internal object SmartPaste {
    /** Normalises copied text for insertion: unix newlines, no trailing blanks, a space after sentence punctuation. */
    fun prepare(before: CharSequence?, clip: String): String {
        val body = clip.replace("\r\n", "\n").replace('\r', '\n').trimEnd()
        if (body.isEmpty()) return ""
        val last = before?.lastOrNull()
        val needsSpace = last != null && last in ".,;:!?" && body.first().isLetterOrDigit()
        return if (needsSpace) " $body" else body
    }
}

internal object PhoneFormat {
    /** Indian-style grouping: 10 digits -> "98765 43210", with country code -> "+91 98765 43210". Null when unrecognised. */
    fun format(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "${digits.substring(0, 5)} ${digits.substring(5)}"
            digits.length == 11 && digits.startsWith("0") -> "${digits.substring(1, 6)} ${digits.substring(6)}"
            digits.length == 12 && digits.startsWith("91") -> "+91 ${digits.substring(2, 7)} ${digits.substring(7)}"
            else -> null
        }
    }
}

internal object EmailTemplates {
    val ALL: List<Pair<String, String>> = listOf(
        "Thanks" to "Hi,\n\nThank you for your email. I appreciate your time and will get back to you shortly.\n\nBest regards",
        "Follow up" to "Hi,\n\nI am following up on my previous message. Could you please let me know the status when you get a chance?\n\nThanks and regards",
        "Meeting" to "Hi,\n\nCould we schedule a short meeting this week? Please let me know a time that suits you.\n\nBest regards",
    )
}

/** User-chosen toolbar order. Unknown or missing labels never disappear: they keep their default position at the end. */
internal object ToolbarOrder {
    val DEFAULT = listOf("Grammar", "Clipboard", "Emoji", "GIF", "Snippets", "Lang", "Settings")

    fun normalise(csv: String, defaults: List<String> = DEFAULT): List<String> {
        val chosen = csv.split(",").map { it.trim() }.filter { it in defaults }.distinct()
        return chosen + defaults.filter { it !in chosen }
    }

    fun <T> apply(items: List<T>, csv: String, label: (T) -> String): List<T> {
        val order = normalise(csv, items.map(label))
        return items.sortedBy { order.indexOf(label(it)) }
    }

    fun move(csv: String, label: String, delta: Int): String {
        val list = normalise(csv).toMutableList()
        val from = list.indexOf(label); if (from < 0) return csv
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.add(to, list.removeAt(from))
        return list.joinToString(",")
    }
}

internal object AccentThemes {
    val NAMES = listOf("default", "blue", "green", "rose", "amber")

    fun accentFor(name: String): Int? = when (name) {
        "blue" -> 0xFF4C8DFF.toInt()
        "green" -> 0xFF2EB67D.toInt()
        "rose" -> 0xFFE8548C.toInt()
        "amber" -> 0xFFF2A93B.toInt()
        else -> null
    }

    private fun channel(c: Int, shift: Int) = (c shr shift) and 0xFF

    fun darken(c: Int, factor: Float = 0.82f): Int {
        fun f(v: Int) = (v * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (f(channel(c, 16)) shl 16) or (f(channel(c, 8)) shl 8) or f(channel(c, 0))
    }

    /** Black or white, whichever has the higher contrast on [c] (WCAG relative luminance). */
    fun onAccentFor(c: Int): Int {
        fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        val lum = 0.2126 * lin(channel(c, 16)) + 0.7152 * lin(channel(c, 8)) + 0.0722 * lin(channel(c, 0))
        return if ((lum + 0.05) / 0.05 >= 1.05 / (lum + 0.05)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
}

/** Whole-utterance voice editing commands. Only an exact command phrase triggers one, so normal dictation is safe. */
internal object VoiceCommands {
    enum class Cmd { DELETE_WORD, DELETE_SENTENCE, SELECT_ALL, SELECT_WORD, SELECT_SENTENCE, NEW_LINE, NEW_PARAGRAPH, UNDO, REDO }

    private val PHRASES: Map<String, Cmd> = mapOf(
        "delete word" to Cmd.DELETE_WORD, "delete last word" to Cmd.DELETE_WORD, "delete that word" to Cmd.DELETE_WORD,
        "delete sentence" to Cmd.DELETE_SENTENCE, "delete last sentence" to Cmd.DELETE_SENTENCE, "delete that sentence" to Cmd.DELETE_SENTENCE,
        "select all" to Cmd.SELECT_ALL, "select everything" to Cmd.SELECT_ALL,
        "select word" to Cmd.SELECT_WORD, "select sentence" to Cmd.SELECT_SENTENCE,
        "new line" to Cmd.NEW_LINE, "next line" to Cmd.NEW_LINE,
        "new paragraph" to Cmd.NEW_PARAGRAPH, "next paragraph" to Cmd.NEW_PARAGRAPH,
        "undo" to Cmd.UNDO, "undo that" to Cmd.UNDO, "redo" to Cmd.REDO, "redo that" to Cmd.REDO,
    )

    private fun normalise(t: String) = t.lowercase().filter { it.isLetter() || it == ' ' }.trim().replace(Regex("\\s+"), " ")

    fun parse(transcript: String): Cmd? = PHRASES[normalise(transcript)]

    /** "heart emoji" -> the first matching emoji, or null. */
    fun emojiFor(transcript: String): String? {
        val t = normalise(transcript)
        if (!t.endsWith(" emoji")) return null
        return EmojiLogic.search(t.removeSuffix(" emoji").trim()).firstOrNull()
    }
}
