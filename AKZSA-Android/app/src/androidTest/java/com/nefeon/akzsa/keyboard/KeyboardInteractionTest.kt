package com.nefeon.akzsa.keyboard

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.nefeon.akzsa.R
import com.nefeon.akzsa.testing.ImeTestActivity
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P5-4: real, device-side tests against the live AKZSA IME — not a mock InputConnection.
 *
 * The keyboard renders in its own window (a different window from [ImeTestActivity]'s), which
 * Espresso's onView() cannot reach — Espresso only sees the host Activity's window. UiAutomator
 * can see and tap into any window on screen, including the IME's, which is what these tests use
 * for anything inside the keyboard. The host Activity's own EditText is still read with normal
 * Activity access, since that is in-process.
 *
 * These tests enable and select AKZSA as the system IME via shell commands run through
 * UiAutomator (`ime enable` / `ime set`), which works from inside an instrumented test without
 * any special CI setup beyond the emulator itself, and restore the previous default IME in
 * [tearDown] so this test suite does not leave the emulator's IME changed for other tests/runs.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardInteractionTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val targetContext = instrumentation.targetContext
    private val imeComponent = "${targetContext.packageName}/com.nefeon.akzsa.keyboard.AkzsaInputMethodService"

    private var previousDefaultIme: String? = null
    private lateinit var scenario: ActivityScenario<ImeTestActivity>

    @Before
    fun setUp() {
        previousDefaultIme = shell("settings get secure default_input_method").trim()
            .takeUnless { it.isEmpty() || it == "null" }

        shell("ime enable $imeComponent")
        shell("ime set $imeComponent")

        val intent = Intent(instrumentation.targetContext, ImeTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        scenario = ActivityScenario.launch(intent)

        tapEditTextAndWaitForKeyboard()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        previousDefaultIme?.let { shell("ime set $it") }
    }

    @Test
    fun tappingALetterKeyTypesItIntoTheField() {
        val key = device.wait(Until.findObject(By.text("a")), 5_000)
        assertNotNull("Expected to find an 'a' key in the live keyboard window", key)
        key!!.click()

        scenario.onActivity { activity ->
            assertTrue(
                "Expected the EditText to contain the tapped letter",
                activity.editText.text.toString().contains("a"),
            )
        }
    }

    @Test
    fun openingEmojiPanelAndTappingAnEmojiInsertsIt() {
        val emojiToggle = findByTextOrDescription("Emoji", "Emoji")
        assertNotNull("Expected the Emoji toolbar key to be visible", emojiToggle)
        emojiToggle!!.click()

        // Any emoji cell carries the "Insert emoji <glyph>" contentDescription added in P5-1/5-3.
        val emojiCell = device.wait(Until.findObject(By.descContains("Insert emoji")), 5_000)
        assertNotNull("Expected the emoji panel to show at least one emoji cell", emojiCell)
        val insertedDescription = emojiCell!!.contentDescription
        emojiCell.click()

        scenario.onActivity { activity ->
            val text = activity.editText.text.toString()
            assertTrue(
                "Expected the tapped emoji to be inserted ($insertedDescription into '$text')",
                text.isNotEmpty(),
            )
        }
    }

    @Test
    fun openingGifPanelShowsTheSearchBox() {
        // GIF is one of the seven toolbar icons.
        val gifToggle = findByTextOrDescription("GIF", "GIF")
        assertNotNull("Expected the GIF toolbar key to be visible", gifToggle)
        gifToggle!!.click()

        val searchHint = targetContext.getString(R.string.gif_search_hint)
        val searchBox = device.wait(Until.findObject(By.textContains(searchHint)), 5_000)
        assertNotNull("Expected the GIF panel's search box hint to be visible", searchBox)
    }

    @Test
    fun bothSpaceZonesBesideTheMicInsertASpaceAndMicDoesNot() {
        val zones = device.wait(Until.findObjects(By.desc("Space")), 5_000)
        assertNotNull("Expected space keys in the live keyboard window", zones)
        // The bottom-row space bar plus the two waveform zones around the microphone.
        assertTrue("Expected at least three space controls, found ${zones!!.size}", zones.size >= 3)
        val before = fieldText()
        zones.minByOrNull { it.visibleBounds.centerX() }!!.click()
        zones.maxByOrNull { it.visibleBounds.centerX() }!!.click()
        assertTrue("Two taps on the side zones should add two spaces", fieldText().length == before.length + 2)
    }

    @Test
    fun sparkleOpensGrammarFix() {
        val sparkle = findByTextOrDescription("Grammar", "Grammar Fix")
        assertNotNull("Expected the Grammar Fix (sparkle) toolbar key", sparkle)
        sparkle!!.click()
        assertNotNull("Expected the AI Workspace panel", device.wait(Until.findObject(By.textContains("AI Workspace")), 5_000))
    }

    private fun fieldText(): String {
        var text = ""
        scenario.onActivity { text = it.editText.text.toString() }
        return text
    }

    private fun tapEditTextAndWaitForKeyboard() {
        scenario.onActivity { it.editText.requestFocus() }
        device.waitForIdle()
        // Give the system a moment to bind and show the IME after the focus request above.
        device.wait(Until.findObject(By.text("a")), 5_000)
    }

    private fun findByTextOrDescription(text: String, description: String): UiObject2? =
        device.wait(Until.findObject(By.text(text)), 2_000)
            ?: device.wait(Until.findObject(By.descContains(description)), 2_000)

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
        }
}
