package com.nefeon.akzsa.keyboard

import com.nefeon.akzsa.keyboard.VoiceCommands.Cmd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalFeaturesTest {
    // ---- glide typing ----
    @Test fun glideHelloAcrossTheKeyboardFindsHello() {
        // h -> g f e r t -> h j k l -> k i o   (passes over extra keys on the way)
        assertEquals("hello", GlideEngine.decode("hgferthjklkio").first())
    }

    @Test fun glideRequiresFirstAndLastLetterAndOrder() {
        val out = GlideEngine.decode("hgferthjklkio")
        assertTrue(out.all { it.first() == 'h' && it.last() == 'o' })
        assertTrue(GlideEngine.decode("o").isEmpty())
        assertTrue(GlideEngine.decode("").isEmpty())
        assertTrue(GlideEngine.decode("xq").isEmpty())
    }

    @Test fun glideShortWord() {
        assertEquals("the", GlideEngine.decode("tghe").first())
    }

    // ---- editing ----
    @Test fun deleteSentenceLength() {
        assertEquals(10, WordEdit.deleteSentenceLength("First one. Second one"))
        assertEquals(12, WordEdit.deleteSentenceLength("First one. Second one! "))
        assertEquals(3, WordEdit.deleteSentenceLength("abc"))
        assertEquals(0, WordEdit.deleteSentenceLength(""))
    }

    @Test fun paragraphRange() {
        val text = "one two\nthree four\nfive"
        assertEquals(8 to 18, WordEdit.paragraphRange(text, 10))
        assertEquals(0 to 7, WordEdit.paragraphRange(text, 3))
        assertNull(WordEdit.paragraphRange("\n\n", 1))
    }

    @Test fun smartPaste() {
        assertEquals("hello", SmartPaste.prepare("x ", "hello  \n"))
        assertEquals(" hello", SmartPaste.prepare("Hi.", "hello"))
        assertEquals("a\nb", SmartPaste.prepare(null, "a\r\nb\r\n"))
        assertEquals("", SmartPaste.prepare("x", "   "))
    }

    // ---- clipboard categories ----
    @Test fun clipboardCategoriesAndFilters() {
        val list = listOf(
            ClipEntry("https://akzsa.app", false, 3), ClipEntry("a@b.com", true, 2), ClipEntry("+91 98765 43210", false, 1), ClipEntry("plain note", false, 0),
        )
        assertEquals("Links", ClipboardLogic.category(list[0].text))
        assertEquals("Emails", ClipboardLogic.category(list[1].text))
        assertEquals("Phones", ClipboardLogic.category(list[2].text))
        assertEquals("Text", ClipboardLogic.category(list[3].text))
        assertEquals(listOf("a@b.com"), ClipboardLogic.filter(list, "Pinned").map { it.text })
        assertEquals(listOf("https://akzsa.app"), ClipboardLogic.filter(list, "Links").map { it.text })
        assertEquals(4, ClipboardLogic.filter(list, "All").size)
    }

    // ---- productivity ----
    @Test fun phoneFormatting() {
        assertEquals("98765 43210", PhoneFormat.format("9876543210"))
        assertEquals("98765 43210", PhoneFormat.format("098765 43210"))
        assertEquals("+91 98765 43210", PhoneFormat.format("+91-9876543210"))
        assertNull(PhoneFormat.format("12345"))
    }

    @Test fun emailTemplatesExist() {
        assertTrue(EmailTemplates.ALL.size >= 3 && EmailTemplates.ALL.all { it.second.length > 40 })
    }

    // ---- toolbar order ----
    @Test fun toolbarOrderNeverLosesButtons() {
        assertEquals(ToolbarOrder.DEFAULT, ToolbarOrder.normalise(""))
        assertEquals(ToolbarOrder.DEFAULT.sorted(), ToolbarOrder.normalise("Emoji,Bogus,Emoji,Settings").sorted())
        assertEquals(listOf("Emoji", "Settings"), ToolbarOrder.normalise("Emoji,Bogus,Emoji,Settings").take(2))
        val moved = ToolbarOrder.move("", "Settings", -1)
        assertEquals("Settings", ToolbarOrder.normalise(moved)[5])
        assertEquals(ToolbarOrder.DEFAULT.first(), ToolbarOrder.normalise(ToolbarOrder.move("", "Grammar", -5)).first())
    }

    @Test fun toolbarOrderAppliesToAnyList() {
        val items = listOf("b", "a", "c")
        assertEquals(listOf("c", "b", "a"), ToolbarOrder.apply(items, "c,b,a") { it })
        assertEquals(items, ToolbarOrder.apply(items, "") { it })
    }

    // ---- accent colours ----
    @Test fun accentColoursHaveReadableTextOnTop() {
        assertNull(AccentThemes.accentFor("default"))
        for (name in AccentThemes.NAMES.drop(1)) {
            val accent = AccentThemes.accentFor(name)!!
            val on = AccentThemes.onAccentFor(accent)
            assertTrue(name, on == 0xFF000000.toInt() || on == 0xFFFFFFFF.toInt())
            assertTrue(name, AccentThemes.darken(accent) != accent)
        }
        assertEquals(0xFF000000.toInt(), AccentThemes.onAccentFor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), AccentThemes.onAccentFor(0xFF101010.toInt()))
    }

    // ---- voice commands ----
    @Test fun voiceCommandsOnlyMatchWholeUtterances() {
        assertEquals(Cmd.DELETE_WORD, VoiceCommands.parse("Delete word."))
        assertEquals(Cmd.NEW_PARAGRAPH, VoiceCommands.parse("new paragraph"))
        assertEquals(Cmd.SELECT_ALL, VoiceCommands.parse("  Select   all "))
        assertEquals(Cmd.UNDO, VoiceCommands.parse("undo that"))
        assertNull(VoiceCommands.parse("please delete word from the file"))
        assertNull(VoiceCommands.parse("hello"))
    }

    @Test fun voiceEmoji() {
        assertEquals(EmojiLogic.search("heart").first(), VoiceCommands.emojiFor("heart emoji"))
        assertNull(VoiceCommands.emojiFor("heart"))
        assertNull(VoiceCommands.emojiFor("zzzzqq emoji"))
    }

    // ---- gif store ----
    @Test fun gifStoreRoundTripAndLists() {
        val a = GifPanel.GifResult("https://x/a.gif", "https://x/a_s.gif", "A\tcat")
        val b = GifPanel.GifResult("https://x/b.gif", "https://x/b_s.gif", "B")
        val decoded = GifStore.decode(GifStore.encode(listOf(a, b)))
        assertEquals(listOf("https://x/a.gif", "https://x/b.gif"), decoded.map { it.url })
        assertEquals("A cat", decoded[0].title)
        assertEquals(listOf("https://x/b.gif", "https://x/a.gif"), GifStore.pushRecent(listOf(a, b), b).map { it.url })
        assertEquals(listOf("https://x/a.gif", "https://x/b.gif"), GifStore.pushRecent(listOf(b, a), a).map { it.url })
        assertTrue(GifStore.toggle(emptyList(), a).size == 1 && GifStore.toggle(listOf(a), a).isEmpty())
        assertTrue(GifStore.decode("http://insecure\tp\tt").isEmpty())      // only https links are accepted
        assertTrue(GifStore.decode(null).isEmpty())
    }
}
