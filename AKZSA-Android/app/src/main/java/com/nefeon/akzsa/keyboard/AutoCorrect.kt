package com.nefeon.akzsa.keyboard

/**
 * Conservative auto-correction: only a curated list of very common typos and missing apostrophes is ever applied
 * automatically (so rare words, names, slang and romanised Hindi are never rewritten). Words in the user's own
 * dictionary are never touched. Everything else is only offered in the suggestion strip.
 */
internal object AutoCorrect {
    private val FIXES: Map<String, String> = mapOf(
        "i" to "I", "im" to "I'm", "ive" to "I've",
        "dont" to "don't", "cant" to "can't", "wont" to "won't", "didnt" to "didn't", "doesnt" to "doesn't",
        "isnt" to "isn't", "wasnt" to "wasn't", "arent" to "aren't", "werent" to "weren't", "havent" to "haven't",
        "hasnt" to "hasn't", "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't",
        "youre" to "you're", "theyre" to "they're", "thats" to "that's", "whats" to "what's",
        "teh" to "the", "adn" to "and", "taht" to "that", "thier" to "their", "wich" to "which", "waht" to "what",
        "recieve" to "receive", "definately" to "definitely", "definatly" to "definitely", "seperate" to "separate",
        "occured" to "occurred", "untill" to "until", "becuase" to "because", "becasue" to "because",
        "beleive" to "believe", "freind" to "friend", "goverment" to "government", "tommorow" to "tomorrow",
        "tomorow" to "tomorrow", "tomorrrow" to "tomorrow", "accomodate" to "accommodate", "wierd" to "weird",
        "adress" to "address", "begining" to "beginning", "calender" to "calendar", "embarass" to "embarrass",
        "enviroment" to "environment", "finaly" to "finally", "gaurd" to "guard", "happend" to "happened",
        "immediatly" to "immediately", "knowlege" to "knowledge", "mispell" to "misspell", "neccessary" to "necessary",
        "noticable" to "noticeable", "perfomance" to "performance", "persue" to "pursue", "posible" to "possible",
        "prefered" to "preferred", "realy" to "really", "rember" to "remember", "responsability" to "responsibility",
        "sincerly" to "sincerely", "succesful" to "successful", "sucess" to "success", "suprise" to "surprise",
        "thoughtfull" to "thoughtful", "truely" to "truly", "writting" to "writing",
        "alot" to "a lot", "thankyou" to "thank you", "thx" to "thanks",
    )

    /** The replacement for [word], or null to leave it alone. [userWordsLower] = the personal dictionary, lower-case. */
    fun fix(word: String, userWordsLower: Set<String>): String? {
        if (word.isEmpty() || word.any { it.isDigit() }) return null
        val lower = word.lowercase()
        if (lower in userWordsLower) return null
        val isLower = word == lower
        val isCapitalised = word == lower.replaceFirstChar { it.uppercaseChar() }
        if (!isLower && !isCapitalised) return null          // ALL CAPS, camelCase, tYpos: leave alone
        if (word == "I") return null
        val fix = FIXES[lower] ?: return null
        if (fix == word) return null
        return if (isCapitalised && !isLower && fix[0].isLowerCase()) fix.replaceFirstChar { it.uppercaseChar() } else fix
    }
}

/** Pure text rules used by [TypingController] (kept here so they can be unit-tested without Android). */
internal object TypingRules {
    const val DOUBLE_SPACE_WINDOW_MS = 500L

    /** True when a space is typed right after "word␣" within the window: the first space becomes ". ". */
    fun shouldDoubleSpacePeriod(before: CharSequence?, msSinceLastSpace: Long): Boolean {
        val t = before ?: return false
        if (msSinceLastSpace !in 1..DOUBLE_SPACE_WINDOW_MS) return false
        if (t.length < 2 || t[t.length - 1] != ' ') return false
        return t[t.length - 2].isLetterOrDigit()
    }
}
