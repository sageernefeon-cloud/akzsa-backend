package com.nefeon.akzsa.keyboard

/**
 * Glide typing: turns the letters a finger passed over (in order) into word candidates from the offline word list.
 * A word matches when it starts and ends on the first and last letter of the path and its letters (doubles collapsed)
 * appear in order along the path. Words that need fewer of the passed-over letters rank first; frequency and the
 * previous word break ties. Pure Kotlin, unit-tested.
 */
internal object GlideEngine {
    private fun collapse(s: String): String {
        val sb = StringBuilder()
        for (c in s) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        return sb.toString()
    }

    private fun isSubsequence(word: String, path: String): Boolean {
        var i = 0
        for (c in path) if (i < word.length && word[i] == c) i++
        return i == word.length
    }

    fun decode(rawPath: String, previousWord: String = "", limit: Int = 3): List<String> {
        val path = collapse(rawPath.lowercase().filter { it.isLetter() })
        if (path.length < 2) return emptyList()
        val boosted = PredictionEngine.nextWords(previousWord, 5)
        return PredictionEngine.allWords().asSequence()
            .filter { it.length >= 2 && it.first() == path.first() && it.last() == path.last() }
            .map { it to collapse(it) }
            .filter { (_, shape) -> isSubsequence(shape, path) }
            .sortedBy { (word, shape) ->
                val skipped = path.length - shape.length
                skipped * 3.0 + PredictionEngine.rank(word) / 12.0 - (if (word in boosted) 25.0 else 0.0) - shape.length * 0.5
            }
            .map { it.first }
            .take(limit).toList()
    }
}
