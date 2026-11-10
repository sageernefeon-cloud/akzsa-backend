package com.nefeon.akzsa.keyboard

/**
 * Offline word prediction (no network, nothing is sent anywhere): a ranked common-word list, a small next-word
 * (bigram) table, prefix completion and edit-distance-1 spelling candidates. Pure Kotlin, unit-tested.
 * English only for now; other languages need their own resources (see docs/GBOARD_PARITY_STATUS.md).
 */
internal object PredictionEngine {
    private val WORDS: List<String> = (
        "the be to of and a in that have i it for not on with he as you do at this but his by from they we say her she or an will my one all would " +
        "there their what so up out if about who get which go me when make can like time no just him know take people into year your good some " +
        "could them see other than then now look only come its over think also back after use two how our work first well way even new want " +
        "because any these give day most us is are was were been being am has had did does done going gone got gotten went said say says " +
        "thanks thank please sorry hello hi hey ok okay yes yeah yep nope maybe sure fine great nice happy birthday morning night evening afternoon " +
        "today tomorrow yesterday tonight later soon again always never sometimes already still really very much more less many few every each " +
        "home office school class meeting call message send sent reply email phone number address place city country world life love friend family " +
        "mother father brother sister wife husband son daughter man woman boy girl child children name tell told ask asked answer question " +
        "need needs needed help helped try tried start started stop stopped keep kept let lets put set run ran walk talk spoke speak feel felt " +
        "find found leave left meet met wait waited bring brought buy bought pay paid sell sold eat ate drink drank sleep slept read write wrote " +
        "free busy ready late early long short big small high low old young right wrong true false same different next last best better worse " +
        "where why while before during between without within under again against both either neither another such own too here there " +
        "something anything nothing everything someone anyone everyone money price cost cheap expensive weekend monday tuesday wednesday thursday " +
        "friday saturday sunday january february march april june july august september october november december water food coffee tea " +
        "lunch dinner breakfast phone photo picture video music movie game book list file document report project plan idea problem " +
        "issue update done finished complete welcome regards best wishes dear sir madam team sorry congratulations"
        ).split(" ").distinct()

    private val RANK: Map<String, Int> = WORDS.withIndex().associate { it.value to it.index }

    private val NEXT: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "have", "will"), "you" to listOf("are", "can", "have"), "we" to listOf("are", "have", "will"),
        "they" to listOf("are", "have", "will"), "he" to listOf("is", "was", "has"), "she" to listOf("is", "was", "has"),
        "it" to listOf("is", "was", "will"), "to" to listOf("be", "the", "do"), "of" to listOf("the", "a", "course"),
        "in" to listOf("the", "a", "this"), "thank" to listOf("you", "god", "u"), "thanks" to listOf("for", "a", "so"),
        "how" to listOf("are", "to", "much"), "what" to listOf("is", "are", "do"), "are" to listOf("you", "we", "they"),
        "am" to listOf("going", "not", "sorry"), "is" to listOf("it", "the", "a"), "do" to listOf("you", "not", "it"),
        "can" to listOf("you", "i", "we"), "will" to listOf("be", "you", "not"), "going" to listOf("to", "home", "out"),
        "good" to listOf("morning", "night", "evening"), "see" to listOf("you", "it", "that"), "let" to listOf("me", "us", "you"),
        "please" to listOf("let", "send", "call"), "sorry" to listOf("for", "about", "i"), "happy" to listOf("birthday", "to", "new"),
        "call" to listOf("me", "you", "back"), "talk" to listOf("to", "later", "soon"), "hello" to listOf("there", "sir", "how"),
        "hi" to listOf("there", "how", "all"), "ok" to listOf("thanks", "i", "see"), "okay" to listOf("thanks", "i", "see"),
        "have" to listOf("a", "to", "been"), "has" to listOf("been", "a", "to"), "was" to listOf("a", "the", "not"),
        "on" to listOf("the", "my", "monday"), "for" to listOf("the", "a", "you"), "with" to listOf("the", "you", "a"),
        "at" to listOf("the", "home", "work"), "dear" to listOf("sir", "madam", "all"), "best" to listOf("regards", "wishes", "of"),
        "see" to listOf("you", "it", "that"), "wait" to listOf("for", "a", "here"), "just" to listOf("a", "wanted", "now"),
    )

    fun allWords(): List<String> = WORDS
    fun rank(word: String): Int = RANK[word] ?: Int.MAX_VALUE
    fun isKnown(word: String): Boolean = RANK.containsKey(word.lowercase())

    /** Next-word suggestions after [previous] (lower-case word), at most [limit]. */
    fun nextWords(previous: String, limit: Int = 3): List<String> = NEXT[previous.lowercase()].orEmpty().take(limit)

    /** Completions of [prefix], boosted when the word follows [previous] in the next-word table. */
    fun completions(prefix: String, previous: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val boosted = NEXT[previous.lowercase()].orEmpty()
        return WORDS.asSequence()
            .filter { it.length > p.length && it.startsWith(p) }
            .sortedBy { (RANK[it] ?: Int.MAX_VALUE) - if (it in boosted) 1000 else 0 }
            .take(limit).toList()
    }

    /** Known words one edit (insert, delete, replace or swap) away from [word]; best-ranked first. Never applied automatically. */
    fun corrections(word: String, limit: Int = 3): List<String> {
        val w = word.lowercase()
        if (w.length < 3 || RANK.containsKey(w)) return emptyList()
        return WORDS.asSequence().filter { it.length in (w.length - 1)..(w.length + 1) && withinOneEdit(w, it) }
            .sortedBy { RANK[it] ?: Int.MAX_VALUE }.take(limit).toList()
    }

    /** Completions first, then spelling candidates, no duplicates. */
    fun suggest(word: String, previous: String, limit: Int = 3): List<String> {
        val out = LinkedHashSet<String>()
        out += completions(word, previous, limit)
        if (out.size < limit) out += corrections(word, limit)
        return out.take(limit).map { matchCase(word, it) }
    }

    fun matchCase(typed: String, suggestion: String): String =
        if (typed.isNotEmpty() && typed[0].isUpperCase() && suggestion.isNotEmpty())
            suggestion.replaceFirstChar { it.uppercaseChar() } else suggestion

    internal fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        val la = a.length; val lb = b.length
        if (kotlin.math.abs(la - lb) > 1) return false
        if (la == lb) {
            var first = -1; var count = 0
            for (i in 0 until la) if (a[i] != b[i]) { if (first < 0) first = i; count++ ; if (count > 2) return false }
            if (count == 1) return true
            return count == 2 && first + 1 < la && a[first] == b[first + 1] && a[first + 1] == b[first]   // swap
        }
        val (s, l) = if (la < lb) a to b else b to a
        var i = 0
        while (i < s.length && s[i] == l[i]) i++
        return s.substring(i) == l.substring(i + 1)
    }
}
