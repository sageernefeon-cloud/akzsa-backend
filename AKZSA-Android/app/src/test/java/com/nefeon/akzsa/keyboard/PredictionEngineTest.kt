package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionEngineTest {
    @Test fun iAmGoSuggestsGoingFirst() {
        val out = Suggestions.build("I am go", "", "", null, smart = true)
        assertEquals("going", out[0].label)
        assertEquals("going ", out[0].insert)
        assertEquals(2, out[0].replaceLength)
        assertTrue(out.size <= Suggestions.MAX)
    }

    @Test fun nextWordIsOfferedAfterASpace() {
        val out = Suggestions.build("thank ", "", "", null, smart = true)
        assertEquals("you", out[0].label)
        assertEquals("you ", out[0].insert)
        assertEquals(0, out[0].replaceLength)
    }

    @Test fun smartOffKeepsOriginalBehaviour() {
        assertTrue(Suggestions.build("I am go", "", "", null).isEmpty())
        assertTrue(Suggestions.build("thank ", "", "", null).isEmpty())
    }

    @Test fun spellingCandidatesAreOneEditAway() {
        assertTrue(PredictionEngine.corrections("helo").contains("hello"))
        assertTrue(PredictionEngine.corrections("hello").isEmpty())      // known word: nothing to correct
        assertTrue(PredictionEngine.corrections("xq").isEmpty())         // too short
    }

    @Test fun editDistanceRules() {
        assertTrue(PredictionEngine.withinOneEdit("teh", "the"))         // swap
        assertTrue(PredictionEngine.withinOneEdit("cat", "cart"))        // insert
        assertTrue(PredictionEngine.withinOneEdit("cart", "cat"))        // delete
        assertTrue(PredictionEngine.withinOneEdit("cat", "cut"))         // replace
        assertFalse(PredictionEngine.withinOneEdit("cat", "dog"))
        assertFalse(PredictionEngine.withinOneEdit("cat", "carts"))
    }

    @Test fun capitalisationIsKept() {
        assertEquals("Going", PredictionEngine.suggest("Go", "am").first())
    }

    @Test fun previousWordIsExtracted() {
        assertEquals("am", Suggestions.previousWord("I am go"))
        assertEquals("hello", Suggestions.previousWord("hello "))
        assertEquals("", Suggestions.previousWord("go"))
    }

    @Test fun personalDictionaryStillComesFirst() {
        val out = Suggestions.build("meet at Ben", "Bengaluru", "", null, smart = true)
        assertEquals("Bengaluru", out[0].label)
    }
}
