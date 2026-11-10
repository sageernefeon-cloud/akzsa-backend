package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLayoutsTest {
    private fun inBlock(key: String, start: Int, end: Int) = key.isNotEmpty() && key.codePoints().allMatch { it in start..end || it in 0x0964..0x0965 }

    @Test fun everyLanguageHasTwoPagesOfFourNonEmptyRowsWithAtMostTenKeys() {
        for (code in NativeLayouts.CODES) {
            val pages = NativeLayouts.pages(code)
            assertEquals(code, 2, pages.size)
            for (page in pages) {
                assertEquals("$code rows", 4, page.size)
                page.forEach { row ->
                    assertTrue("$code row $row", row.isNotEmpty())
                    assertTrue("$code row too long $row", row.size <= 10)
                }
            }
        }
    }

    @Test fun indicKeysStayInsideTheirOwnUnicodeBlock() {
        for (s in NativeLayouts.SCRIPTS) for (page in 0..1) NativeLayouts.rows(s.code, page).flatten().forEach { key ->
            assertTrue("${s.code} key '$key'", inBlock(key, s.blockStart, s.blockEnd))
        }
    }

    @Test fun arabicAndUrduKeysAreInTheArabicBlocks() {
        for (code in listOf("ar", "ur")) for (page in 0..1) NativeLayouts.rows(code, page).flatten().forEach { key ->
            assertTrue("$code '$key'", key.codePoints().allMatch { it in 0x0600..0x06FF })
        }
    }

    @Test fun noDuplicateKeysOnAPage() {
        for (code in NativeLayouts.CODES) for (page in 0..1) {
            val keys = NativeLayouts.rows(code, page).flatten()
            assertEquals("$code page $page", keys.size, keys.distinct().size)
        }
    }

    @Test fun coreLettersArePresent() {
        assertTrue(NativeLayouts.rows("kn", 0).flatten().contains("ಕ"))     // ka
        assertTrue(NativeLayouts.rows("ta", 0).flatten().contains("க"))
        assertTrue(NativeLayouts.rows("te", 0).flatten().contains("క"))
        assertTrue(NativeLayouts.rows("bn", 0).flatten().contains("ক"))
        assertTrue(NativeLayouts.rows("gu", 0).flatten().contains("ક"))
        assertTrue(NativeLayouts.rows("ml", 0).flatten().contains("ക"))
        assertTrue(NativeLayouts.rows("pa", 0).flatten().contains("ਕ"))
        assertTrue(NativeLayouts.rows("hi", 0).flatten().contains("क"))
    }

    @Test fun hindiLayoutIsUnchanged() {
        assertEquals(DevanagariLayout.page0.flatten().size, NativeLayouts.rows("hi", 0).flatten().size)
        assertEquals("अ", NativeLayouts.toggleLabel("hi", 0))
    }

    @Test fun distributeKeepsAllKeysInOrder() {
        val keys = (1..23).map { it.toString() }
        val rows = NativeLayouts.distribute(keys)
        assertEquals(keys, rows.flatten())
        assertEquals(4, rows.size)
        assertTrue(rows.maxOf { it.size } - rows.minOf { it.size } <= 1)
    }
}
