package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiAndClipboardTest {
    // ---- emoji ----
    @Test fun recentsAreMostRecentFirstWithoutDuplicatesAndCapped() {
        var list = emptyList<String>()
        for (e in listOf("😀", "😂", "😀")) list = EmojiLogic.pushRecent(list, e)
        assertEquals(listOf("😀", "😂"), list)
        val many = (1..50).fold(emptyList<String>()) { acc, i -> EmojiLogic.pushRecent(acc, "e$i") }
        assertEquals(EmojiLogic.MAX_RECENTS, many.size)
        assertEquals("e50", many.first())
    }

    @Test fun favouritesToggleAndRoundTrip() {
        val on = EmojiLogic.toggle(emptyList(), "⭐")
        assertEquals(listOf("⭐"), on)
        assertTrue(EmojiLogic.toggle(on, "⭐").isEmpty())
        assertEquals(listOf("a", "b"), EmojiLogic.decode(EmojiLogic.encode(listOf("a", "b"))))
        assertTrue(EmojiLogic.decode(null).isEmpty())
    }

    @Test fun skinToneOnlyAppliesToSupportedEmoji() {
        assertEquals("👍\uD83C\uDFFD", EmojiLogic.withSkinTone("👍", 3))
        assertEquals("👍", EmojiLogic.withSkinTone("👍", 0))
        assertEquals("😀", EmojiLogic.withSkinTone("😀", 4))
        assertEquals("✌\uD83C\uDFFB", EmojiLogic.withSkinTone("✌️", 1))   // variation selector replaced by the modifier
    }

    @Test fun searchFindsExactPrefixAndPartialKeywords() {
        assertEquals("😂", EmojiLogic.search("lol").first())
        assertTrue(EmojiLogic.search("birth").contains("🎂"))
        assertTrue(EmojiLogic.search("   ").isEmpty())
        assertTrue(EmojiLogic.search("zzzzqq").isEmpty())
        assertEquals("❤️", EmojiLogic.forWord("love"))
        assertNull(EmojiLogic.forWord("l"))
    }

    @Test fun typingAWordOffersAnEmojiSuggestion() {
        val out = Suggestions.build("I love", "", "", null, smart = true)
        assertTrue(out.any { it.label == "❤️" })
    }

    // ---- clipboard ----
    @Test fun sensitiveTextIsNeverRemembered() {
        for (t in listOf("123456", "4111 1111 1111 1111", "Your OTP is 482913", "password: hunter2 1234", "9876")) {
            assertTrue(t, ClipboardLogic.looksSensitive(t))
            assertTrue(t, ClipboardLogic.add(emptyList(), t, 1L).isEmpty())
        }
        assertFalse(ClipboardLogic.looksSensitive("Meet me at 6pm near the station"))
        assertEquals(1, ClipboardLogic.add(emptyList(), "Meet me at 6pm", 1L).size)
    }

    @Test fun newestFirstAndRecopyKeepsPin() {
        var list = ClipboardLogic.add(emptyList(), "first", 1L)
        list = ClipboardLogic.add(list, "second", 2L)
        list = ClipboardLogic.togglePin(list, "first")
        list = ClipboardLogic.add(list, "first", 3L)
        assertEquals(listOf("first", "second"), list.map { it.text })
        assertTrue(list.first().pinned)
    }

    @Test fun pinnedClipsFirstInDisplayOrderAndSurviveEviction() {
        var list = ClipboardLogic.add(emptyList(), "keep me", 0L)
        list = ClipboardLogic.togglePin(list, "keep me")
        for (i in 1..40) list = ClipboardLogic.add(list, "clip $i", i.toLong())
        assertEquals(ClipboardLogic.MAX_ITEMS, list.size)
        assertTrue(list.any { it.text == "keep me" && it.pinned })
        assertEquals("keep me", ClipboardLogic.ordered(list).first().text)
    }

    @Test fun expiryDropsOnlyOldUnpinnedClips() {
        val list = listOf(ClipEntry("old", false, 0L), ClipEntry("pinned old", true, 0L), ClipEntry("fresh", false, 90L))
        val kept = ClipboardLogic.prune(list, now = 100L, retentionMs = 50L).map { it.text }
        assertEquals(listOf("pinned old", "fresh"), kept)
    }

    @Test fun storageFormatRoundTripsNewlinesPipesAndBackslashes() {
        val list = listOf(ClipEntry("a|b\nline2 \\ end", true, 5L), ClipEntry("plain", false, 7L))
        assertEquals(list, ClipboardLogic.decode(ClipboardLogic.encode(list)))
        assertTrue(ClipboardLogic.decode("garbage").isEmpty())
        assertTrue(ClipboardLogic.decode(null).isEmpty())
    }

    @Test fun searchAndRemove() {
        val list = ClipboardLogic.add(ClipboardLogic.add(emptyList(), "Hello World", 1L), "other", 2L)
        assertEquals(listOf("Hello World"), ClipboardLogic.search(list, "world").map { it.text })
        assertEquals(listOf("other"), ClipboardLogic.remove(list, "Hello World").map { it.text })
    }

    @Test fun smartExtraction() {
        val text = "Call +91 98765 43210 or mail a.b@example.com, see https://akzsa.app/x. Due 12/05/2026"
        val got = ClipboardLogic.extract(text).associate { it.kind to it.value }
        assertEquals("https://akzsa.app/x", got["url"])
        assertEquals("a.b@example.com", got["email"])
        assertTrue(got["phone"]!!.replace(" ", "").endsWith("9876543210"))
        assertEquals("12/05/2026", got["date"])
        assertTrue(ClipboardLogic.extract("nothing special here").isEmpty())
    }

    @Test fun copiedLongTextOffersItsEmailInTheStrip() {
        val out = Suggestions.build("write to ", "", "", "Contact: ravi@example.com for details", smart = true)
        assertTrue(out.any { it.insert == "ravi@example.com" })
    }
}
