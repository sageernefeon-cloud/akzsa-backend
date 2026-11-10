package com.nefeon.akzsa.keyboard

/** Pure helpers for Backtrack ("actually, make that 6pm") so the replacement matches how the original was inserted. */
object BacktrackText {
    /**
     * The model returns just the corrected phrase. The text it replaces was inserted with whatever spacing and
     * capitalisation AKZSA applied (for example a trailing space, or a capital first letter), so carry those over.
     */
    fun carryEdgeFormatting(expected: String, replacement: String): String {
        val core = replacement.trim()
        if (core.isEmpty()) return replacement
        val lead = expected.takeWhile { it.isWhitespace() }
        val trail = expected.takeLastWhile { it.isWhitespace() }
        val expectedCore = expected.trim()
        val shouldCapitalise = expectedCore.firstOrNull()?.isUpperCase() == true && core.first().isLowerCase()
        val fixedCore = if (shouldCapitalise) core.replaceFirstChar { it.uppercaseChar() } else core
        return lead + fixedCore + trail
    }
}
