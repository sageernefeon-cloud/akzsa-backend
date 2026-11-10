package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyAccentsTest {
    @Test fun lowerCaseVowelHasAccents() {
        val e = KeyAccents.forLabel("e")
        assertTrue(e.contains("é"))
        assertTrue(e.all { it == it.lowercase() })
    }

    @Test fun upperCaseLabelGivesUpperCaseAccents() {
        val e = KeyAccents.forLabel("E")
        assertTrue(e.contains("É"))
        assertTrue(e.all { it == it.uppercase() })
    }

    @Test fun lengthChangingUppercaseIsKept() {
        // "ß".uppercase() is "SS" (two characters); the key keeps ß instead of inserting two letters.
        assertTrue(KeyAccents.forLabel("S").contains("ß"))
        assertTrue(KeyAccents.forLabel("S").none { it.length != 1 })
    }

    @Test fun punctuationHasAlternatives() {
        assertTrue(KeyAccents.forLabel(".").containsAll(listOf("?", "!", ",")))
    }

    @Test fun keysWithoutAlternativesAreEmpty() {
        for (label in listOf("", "q", "space", "क", "⌫", "1")) assertEquals(label, emptyList<String>(), KeyAccents.forLabel(label))
    }

    @Test fun neverMoreThanMaxOptions() {
        for (c in 'a'..'z') assertTrue(KeyAccents.forLabel(c.toString()).size <= KeyAccents.MAX_OPTIONS)
    }
}
