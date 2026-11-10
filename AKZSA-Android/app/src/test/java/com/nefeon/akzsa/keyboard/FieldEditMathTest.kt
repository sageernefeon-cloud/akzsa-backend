package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldEditMathTest {
    @Test fun lastWords() {
        assertEquals(10, FieldEditMath.suffixStart("hello big wide world", 2, true))
        assertEquals(0, FieldEditMath.suffixStart("one two", 2, true))
    }
    @Test fun tooFewWordsOrChars() {
        assertNull(FieldEditMath.suffixStart("one two", 3, true))
        assertNull(FieldEditMath.suffixStart("abc", 4, false))
        assertNull(FieldEditMath.suffixStart("abc", 0, false))
    }
    @Test fun charsCountCodePointsNotUtf16Units() {
        val s = "a\uD83D\uDE00b"          // a, grinning face (2 UTF-16 units), b
        assertEquals(1, FieldEditMath.suffixStart(s, 2, false))
        assertEquals(0, FieldEditMath.suffixStart(s, 3, false))
    }
    @Test fun extraWhitespaceIgnored() {
        assertEquals(7, FieldEditMath.suffixStart("a  b   c", 1, true))
    }
}
