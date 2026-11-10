package com.nefeon.akzsa.keyboard

/** Pure gesture maths (no Android types, unit-tested). */
internal object SwipeGestures {
    /** Cursor steps for a horizontal drag of [dxPx] from the touch-down point; negative = left. Truncates toward zero. */
    fun cursorSteps(dxPx: Float, stepPx: Float): Int = if (stepPx <= 0f) 0 else (dxPx / stepPx).toInt()

    /** Words to delete for a leftward drag of [dxPx] on the backspace key; rightward drags delete nothing. */
    fun wordDeleteCount(dxPx: Float, stepPx: Float): Int = if (dxPx >= 0f || stepPx <= 0f) 0 else (-dxPx / stepPx).toInt()
}

/** Pure word / sentence boundary rules used by delete-word and the select buttons (unit-tested). */
internal object WordEdit {
    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '_'
    private const val TERMINATORS = ".!?\n\u0964"   // includes the Devanagari danda

    /** UTF-16 units to delete for "delete previous word": trailing spaces, then one run of word (or punctuation) characters. */
    fun deleteWordLength(before: CharSequence?): Int {
        val t = before ?: return 0
        var i = t.length
        while (i > 0 && t[i - 1].isWhitespace()) i--
        if (i == 0) return t.length
        val word = isWordChar(t[i - 1])
        while (i > 0 && !t[i - 1].isWhitespace() && isWordChar(t[i - 1]) == word) i--
        return t.length - i
    }

    /** [start, end) of the word at or just before [cursor], or null when there is none. */
    fun wordRange(text: String, cursor: Int): Pair<Int, Int>? {
        val c = cursor.coerceIn(0, text.length)
        var s = c; var e = c
        while (s > 0 && isWordChar(text[s - 1])) s--
        while (e < text.length && isWordChar(text[e])) e++
        return if (e > s) s to e else null
    }

    /** UTF-16 units to delete for "delete previous sentence" (back to just after the previous terminator, trailing spaces included). */
    fun deleteSentenceLength(before: CharSequence?): Int {
        val t = before ?: return 0
        var end = t.length
        while (end > 0 && t[end - 1].isWhitespace()) end--
        if (end == 0) return t.length
        var i = end
        // the sentence's own terminator (if the cursor follows one) is part of it
        if (TERMINATORS.indexOf(t[i - 1]) >= 0 && t[i - 1] != '\n') i--
        while (i > 0 && TERMINATORS.indexOf(t[i - 1]) < 0) i--
        while (i < end && t[i].isWhitespace()) i++
        return t.length - i
    }

    /** [start, end) of the paragraph (text between line breaks) around [cursor], or null when empty. */
    fun paragraphRange(text: String, cursor: Int): Pair<Int, Int>? {
        val c = cursor.coerceIn(0, text.length)
        var s = c
        while (s > 0 && text[s - 1] != '\n') s--
        var e = c
        while (e < text.length && text[e] != '\n') e++
        return if (e > s && text.substring(s, e).isNotBlank()) s to e else null
    }

    /** [start, end) of the sentence around [cursor] (end includes its terminator), or null when empty. */
    fun sentenceRange(text: String, cursor: Int): Pair<Int, Int>? {
        val c = cursor.coerceIn(0, text.length)
        var s = c
        while (s > 0 && TERMINATORS.indexOf(text[s - 1]) < 0) s--
        while (s < text.length && text[s].isWhitespace() && s < c) s++
        var e = c
        while (e < text.length && TERMINATORS.indexOf(text[e]) < 0) e++
        if (e < text.length) e++
        return if (e > s && text.substring(s, e).isNotBlank()) s to e else null
    }
}
