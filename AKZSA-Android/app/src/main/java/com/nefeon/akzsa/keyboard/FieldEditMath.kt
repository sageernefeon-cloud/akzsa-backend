package com.nefeon.akzsa.keyboard

/** Pure helpers for "delete the last N words/characters" so the rule can be unit-tested. */
internal object FieldEditMath {
    private val WORD = Regex("\\S+")

    /** Index in [whole] where the last [count] words (or code points) start, or null if there are fewer. */
    fun suffixStart(whole: String, count: Int, words: Boolean): Int? {
        if (count < 1) return null
        return if (words) {
            val matches = WORD.findAll(whole).toList()
            if (matches.size < count) null else matches[matches.size - count].range.first
        } else {
            if (whole.codePointCount(0, whole.length) < count) null else whole.offsetByCodePoints(whole.length, -count)
        }
    }
}
