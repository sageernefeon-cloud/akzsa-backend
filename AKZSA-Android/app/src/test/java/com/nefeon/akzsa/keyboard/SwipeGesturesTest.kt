package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeGesturesTest {
    @Test fun cursorStepsFollowTheDragAndTruncateTowardZero() {
        assertEquals(0, SwipeGestures.cursorSteps(11f, 12f))
        assertEquals(1, SwipeGestures.cursorSteps(13f, 12f))
        assertEquals(-2, SwipeGestures.cursorSteps(-25f, 12f))
        assertEquals(0, SwipeGestures.cursorSteps(100f, 0f))
    }

    @Test fun onlyLeftwardBackspaceDragsDeleteWords() {
        assertEquals(0, SwipeGestures.wordDeleteCount(30f, 28f))
        assertEquals(0, SwipeGestures.wordDeleteCount(-20f, 28f))
        assertEquals(1, SwipeGestures.wordDeleteCount(-30f, 28f))
        assertEquals(3, SwipeGestures.wordDeleteCount(-90f, 28f))
    }

    @Test fun deleteWordLength() {
        assertEquals(5, WordEdit.deleteWordLength("hello"))
        assertEquals(5, WordEdit.deleteWordLength("hi hello"))
    }

    @Test fun deleteWordSkipsTrailingSpacesFirst() {
        assertEquals(7, WordEdit.deleteWordLength("a hello  "))
        assertEquals(1, WordEdit.deleteWordLength("what?"))               // punctuation run is its own "word"
        assertEquals(0, WordEdit.deleteWordLength(""))
        assertEquals(3, WordEdit.deleteWordLength("   "))
        assertEquals(5, WordEdit.deleteWordLength("don't"))
    }

    @Test fun wordRangeAtAndAfterWord() {
        assertEquals(6 to 11, WordEdit.wordRange("hello world", 8))
        assertEquals(6 to 11, WordEdit.wordRange("hello world", 11))     // cursor at the end of the word
        assertEquals(0 to 5, WordEdit.wordRange("hello world", 5))       // cursor right after "hello"
        assertNull(WordEdit.wordRange("   ", 1))
    }

    @Test fun sentenceRange() {
        val text = "First one. Second one! Third"
        assertEquals(0 to 10, WordEdit.sentenceRange(text, 4))
        assertEquals(11 to 22, WordEdit.sentenceRange(text, 15))
        assertEquals(23 to text.length, WordEdit.sentenceRange(text, text.length))
        assertNull(WordEdit.sentenceRange("", 0))
    }
}
