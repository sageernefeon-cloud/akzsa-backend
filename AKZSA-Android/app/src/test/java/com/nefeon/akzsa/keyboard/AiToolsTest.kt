package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolsTest {
    @Test fun everyTaskHasOptionsWithUniqueLabels() {
        for (task in AiTask.values()) {
            val options = AiTools.options(task)
            assertTrue("$task has no options", options.isNotEmpty())
            assertEquals("duplicate labels in $task", options.size, options.map { it.label }.toSet().size)
        }
    }

    @Test fun optionLabelsFitAKeyAndAreNotBlank() {
        // The key row has at most 7 keys (back key + 6 options) on a 360dp phone; keep labels short.
        for (task in AiTask.values()) {
            assertTrue("$task has too many options", AiTools.options(task).size <= 6)
            for (o in AiTools.options(task)) assertTrue("label '${o.label}'", o.label.isNotBlank() && o.label.length <= 8)
        }
    }

    @Test fun translateOptionsCarryALanguageAndOthersCarryAStyle() {
        for (o in AiTools.options(AiTask.TRANSLATE)) { assertTrue(!o.language.isNullOrBlank()); assertNull(o.style) }
        for (task in listOf(AiTask.REWRITE, AiTask.REPLY, AiTask.STRUCTURE)) {
            for (o in AiTools.options(task)) { assertTrue("${o.label} needs a style", !o.style.isNullOrBlank()); assertNull(o.language) }
        }
    }

    @Test fun wireNamesMatchTheBackend() {
        assertEquals(setOf("reply", "rewrite", "translate", "structure"), AiTask.values().map { it.wire }.toSet())
    }

    @Test fun describeNamesToolAndOption() {
        assertEquals("Rewrite · Polite", AiTools.describe(AiTask.REWRITE, AiOption("Polite", style = "polite")))
    }

    @Test fun everyToolHasAnEmptyHint() {
        for (task in AiTask.values()) assertTrue(AiTools.emptyHint(task).length > 20)
    }

    @Test fun personaIsOneLineTrimmedAndCapped() {
        assertNull(AiTools.cleanPersona(null))
        assertNull(AiTools.cleanPersona("  \n \t "))
        assertEquals("short and friendly, Hinglish", AiTools.cleanPersona("  short and friendly,\n Hinglish  "))
        assertEquals(AiTools.MAX_PERSONA, AiTools.cleanPersona("x".repeat(1000))!!.length)
        assertFalse(AiTools.cleanPersona("a\nb")!!.contains('\n'))
    }
}
