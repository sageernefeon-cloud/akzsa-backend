package com.nefeon.akzsa.keyboard

/**
 * Real, local typing suggestions (no AI call, nothing invented):
 *  - while a word is being typed: entries of the user's personal dictionary that start with it;
 *  - between words: the user's saved snippets and the last copied text.
 */
internal object Suggestions {
    const val MAX = 3
    private const val MAX_LABEL = 16

    data class Suggestion(val label: String, val insert: String, val replaceLength: Int)

    /** The word being typed: the trailing run of letters, digits and apostrophes before the cursor. */
    fun currentWord(before: CharSequence?): String {
        val text = before?.toString().orEmpty().toString()
        var i = text.length
        while (i > 0 && (text[i - 1].isLetterOrDigit() || text[i - 1] == '\'')) i--
        return text.substring(i)
    }

    /** The word before the one being typed (lower-case letters only), or "" at the start of the text. */
    fun previousWord(before: CharSequence?): String {
        val text = before?.toString().orEmpty().toString()
        val head = text.substring(0, text.length - currentWord(text).length).trimEnd()
        var i = head.length
        while (i > 0 && (head[i - 1].isLetter() || head[i - 1] == '\'')) i--
        return head.substring(i).lowercase()
    }

    /** [smart] adds the offline prediction engine (completions, spelling candidates, next word); off keeps the original behaviour. */
    fun build(before: CharSequence?, dictionaryRaw: String, snippetRaw: String, lastCopied: String?, smart: Boolean = false): List<Suggestion> {
        val word = currentWord(before)
        val out = ArrayList<Suggestion>()
        if (word.isNotEmpty()) {
            val lower = word.lowercase()
            for (entry in UserDictionary.parse(dictionaryRaw)) {
                val written = entry.written
                if (written.length > word.length && written.lowercase().startsWith(lower) && out.none { it.label == written }) {
                    out += Suggestion(written, "$written ", word.length)
                    if (out.size == MAX) break
                }
            }
            if (smart && out.size < MAX) {
                for (w in PredictionEngine.suggest(word, previousWord(before), MAX)) {
                    if (out.size == MAX) break
                    if (w != word && out.none { it.label.equals(w, ignoreCase = true) }) out += Suggestion(w, "$w ", word.length)
                }
                val emoji = EmojiLogic.forWord(word)
                if (emoji != null && out.size < MAX) out += Suggestion(emoji, " $emoji ", 0)
            }
            return out
        }
        if (smart && before?.toString().orEmpty().endsWith(" ")) {
            for (w in PredictionEngine.nextWords(previousWord(before), 2)) out += Suggestion(w, "$w ", 0)
        }
        for (entry in SnippetDictionary.parse(snippetRaw).take(2)) {
            out += Suggestion(shorten(entry.trigger), entry.expansion, 0)
        }
        val clip = lastCopied?.trim().orEmpty()
        if (smart && clip.isNotEmpty()) {
            // Offer the useful piece (link, email, phone, date) of a longer copied text instead of pasting everything.
            val parts = ClipboardLogic.extract(clip)
            if (parts.isNotEmpty() && (parts.size > 1 || parts[0].value != clip)) {
                for (part in parts) {
                    if (out.size >= MAX) break
                    out += Suggestion(shorten(part.value), part.value, 0)
                }
            }
        }
        if (clip.isNotEmpty() && out.size < MAX && !clip.contains('\n') && out.none { it.insert == clip }) {
            out += Suggestion(shorten(clip), clip, 0)
        }
        return out
    }

    private fun shorten(s: String): String = if (s.length <= MAX_LABEL) s else s.take(MAX_LABEL - 1).trimEnd() + "…"
}
