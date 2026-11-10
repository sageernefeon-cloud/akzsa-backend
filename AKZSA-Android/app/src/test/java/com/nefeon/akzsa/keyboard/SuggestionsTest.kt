package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val dict = "NEFEON, AKZSA, nefion = NEFEON, Bengaluru"

    @Test fun currentWordIsTheTrailingRun() {
        assertEquals("nef", Suggestions.currentWord("hello nef"))
        assertEquals("", Suggestions.currentWord("hello "))
        assertEquals("don't", Suggestions.currentWord("I don't"))
    }

    @Test fun typingAWordSuggestsMatchingDictionaryEntries() {
        val out = Suggestions.build("meet at Ben", dict, "", null)
        assertEquals(1, out.size)
        assertEquals("Bengaluru", out[0].label)
        assertEquals("Bengaluru ", out[0].insert)
        assertEquals(3, out[0].replaceLength)
    }

    @Test fun exactMatchIsNotSuggestedAgain() {
        assertTrue(Suggestions.build("NEFEON", dict, "", null).isEmpty())
    }

    @Test fun betweenWordsSnippetsAndLastCopiedTextAreOffered() {
        val snippets = "my signature = Best regards,\nJordan\n---\nmy address = 221B Baker Street"
        val out = Suggestions.build("hello ", dict, snippets, "Good morning!")
        assertEquals(listOf("my signature", "my address", "Good morning!"), out.map { it.label })
        assertEquals("Best regards,\nJordan", out[0].insert)
        assertEquals(0, out[0].replaceLength)
    }

    @Test fun nothingIsInventedWhenThereIsNothingToOffer() {
        assertTrue(Suggestions.build("", "", "", null).isEmpty())
        assertTrue(Suggestions.build("hello wor", "", "", null).isEmpty())
    }

    @Test fun multiLineClipboardIsNotShownAsAChip() {
        assertTrue(Suggestions.build("", "", "", "line one\nline two").isEmpty())
    }
}
