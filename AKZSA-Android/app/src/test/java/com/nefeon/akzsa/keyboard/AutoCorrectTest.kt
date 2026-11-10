package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCorrectTest {
    private val none = emptySet<String>()

    @Test fun commonTyposAndApostrophesAreFixed() {
        assertEquals("don't", AutoCorrect.fix("dont", none))
        assertEquals("the", AutoCorrect.fix("teh", none))
        assertEquals("I'm", AutoCorrect.fix("im", none))
        assertEquals("I", AutoCorrect.fix("i", none))
        assertEquals("definitely", AutoCorrect.fix("definately", none))
    }

    @Test fun capitalisedInputKeepsItsCapital() {
        assertEquals("Don't", AutoCorrect.fix("Dont", none))
        assertEquals("The", AutoCorrect.fix("Teh", none))
        assertEquals("I'm", AutoCorrect.fix("Im", none))
    }

    @Test fun unknownRareOrSlangWordsAreNeverChanged() {
        for (w in listOf("nope", "lol", "bro", "namaste", "Rohan", "kya", "hello", "tbh")) assertNull(w, AutoCorrect.fix(w, none))
    }

    @Test fun acronymsMixedCaseDigitsAndAlreadyCorrectAreLeftAlone() {
        for (w in listOf("DONT", "dOnt", "teh2", "I", "")) assertNull(w, AutoCorrect.fix(w, none))
    }

    @Test fun personalDictionaryWinsOverAutoCorrect() {
        assertNull(AutoCorrect.fix("dont", setOf("dont")))
    }

    @Test fun doubleSpaceRule() {
        assertTrue(TypingRules.shouldDoubleSpacePeriod("hello ", 200))
        assertTrue(TypingRules.shouldDoubleSpacePeriod("a ", 1))
        assertFalse(TypingRules.shouldDoubleSpacePeriod("hello ", 600))     // too slow
        assertFalse(TypingRules.shouldDoubleSpacePeriod("hello ", 0))
        assertFalse(TypingRules.shouldDoubleSpacePeriod("hello", 200))      // no space yet
        assertFalse(TypingRules.shouldDoubleSpacePeriod("hello. ", 200))    // already punctuated
        assertFalse(TypingRules.shouldDoubleSpacePeriod("hello  ", 200))    // not after two spaces
        assertFalse(TypingRules.shouldDoubleSpacePeriod(" ", 200))
        assertFalse(TypingRules.shouldDoubleSpacePeriod(null, 200))
    }
}
