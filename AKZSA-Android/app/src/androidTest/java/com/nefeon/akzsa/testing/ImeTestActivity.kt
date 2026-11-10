package com.nefeon.akzsa.testing

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Test-only host Activity for the P5-4 instrumented tests. It only exists in the androidTest
 * APK (declared in src/androidTest/AndroidManifest.xml), never in the shipped app. Its single
 * EditText is what the real AKZSA IME attaches to while a test enables/sets it as the system
 * input method via UiAutomator shell commands and taps keys in its window.
 */
class ImeTestActivity : Activity() {
    lateinit var editText: EditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        editText = EditText(this).apply {
            id = android.R.id.edit
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(editText)
        }
        setContentView(root)
        editText.requestFocus()
    }
}
