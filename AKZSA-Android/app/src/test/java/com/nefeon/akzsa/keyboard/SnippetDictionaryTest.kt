package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnippetDictionaryTest {
    private val raw = "my signature = Best regards,\nJordan\n+1 555 0100\n---\nmy address = 221B Baker Street"

    @Test fun parsesMultiLineAndSingleLineSnippets() {
        val entries = SnippetDictionary.parse(raw)
        assertEquals(2, entries.size)
        assertEquals("Best regards,\nJordan\n+1 555 0100", entries[0].expansion)
        assertEquals("221B Baker Street", entries[1].expansion)
    }
    @Test fun matchesWholeUtteranceIgnoringCaseAndFullStop() {
        assertEquals("221B Baker Street", SnippetDictionary.match("My address.", raw))
    }
    @Test fun doesNotMatchWhenTheTriggerIsOnlyPartOfTheSentence() {
        assertNull(SnippetDictionary.match("please send my address to Sam", raw))
    }
    @Test fun blankInputsNeverMatch() {
        assertNull(SnippetDictionary.match("", raw))
        assertNull(SnippetDictionary.match("my address", ""))
    }
    @Test fun expansionMayContainEqualsSign() {
        assertEquals("a=b", SnippetDictionary.match("formula", "formula = a=b"))
    }
    @Test fun entriesWithoutAnExpansionAreSkipped() {
        assertEquals(0, SnippetDictionary.parse("lonely trigger =").size)
    }
}
