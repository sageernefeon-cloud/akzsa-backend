package com.nefeon.akzsa.keyboard

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import com.nefeon.akzsa.R

object CursorRow {

    fun build(
        context: Context,
        dp: (Int) -> Int,
        textColor: () -> Int,
        specialKeyColor: () -> Int,
        borderColor: () -> Int,
        getInputConnection: () -> InputConnection?,
        onSelectAll: () -> Unit,
        onCopy: () -> Unit,
        onPaste: () -> Unit,
        onCut: () -> Unit,
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        fun makeKey(label: String, description: String = label, action: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                contentDescription = description
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(textColor())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(specialKeyColor())
                    setStroke(dp(1), borderColor())
                }
                setOnClickListener { action() }
            }
        }

        fun sendKey(keyCode: Int, withShift: Boolean = false) {
            val ic = getInputConnection() ?: return
            if (withShift) {
                ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                    keyCode, 0, KeyEvent.META_SHIFT_ON))
                ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP,
                    keyCode, 0, KeyEvent.META_SHIFT_ON))
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
        }

        val keys = listOf(
            makeKey(context.getString(R.string.cursor_select_all)) { onSelectAll() },
            makeKey(context.getString(R.string.cursor_cut)) { onCut() },
            makeKey(context.getString(R.string.cursor_copy)) { onCopy() },
            makeKey(context.getString(R.string.cursor_paste)) { onPaste() },
            makeKey("◀", context.getString(R.string.cd_cursor_left)) { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) },
            makeKey("▶", context.getString(R.string.cd_cursor_right)) { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) },
            makeKey("▲", context.getString(R.string.cd_cursor_up)) { sendKey(KeyEvent.KEYCODE_DPAD_UP) },
            makeKey("▼", context.getString(R.string.cd_cursor_down)) { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) },
        )

        keys.forEach { key ->
            row.addView(key, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(2), dp(1), dp(2), dp(1))
            })
        }

        return row
    }

    /** Second row: select word / sentence, delete word, undo, redo. */
    fun buildEditRow(
        context: Context,
        dp: (Int) -> Int,
        textColor: () -> Int,
        specialKeyColor: () -> Int,
        borderColor: () -> Int,
        onSelectWord: () -> Unit,
        onSelectSentence: () -> Unit,
        onDeleteWord: () -> Unit,
        onUndo: () -> Unit,
        onRedo: () -> Unit,
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        fun makeKey(label: String, description: String, action: () -> Unit) = TextView(context).apply {
            text = label
            contentDescription = description
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(textColor())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(specialKeyColor())
                setStroke(dp(1), borderColor())
            }
            setOnClickListener { action() }
        }
        listOf(
            makeKey("Word", "Select word", onSelectWord),
            makeKey("Sentence", "Select sentence", onSelectSentence),
            makeKey("⌫ Word", "Delete previous word", onDeleteWord),
            makeKey("Undo", "Undo", onUndo),
            makeKey("Redo", "Redo", onRedo),
        ).forEach { key ->
            row.addView(key, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), dp(1), dp(2), dp(1)) })
        }
        return row
    }

    /** Third row: select paragraph, extend selection left/right, delete sentence. */
    fun buildEditRow2(
        context: Context,
        dp: (Int) -> Int,
        textColor: () -> Int,
        specialKeyColor: () -> Int,
        borderColor: () -> Int,
        onSelectParagraph: () -> Unit,
        onExtendLeft: () -> Unit,
        onExtendRight: () -> Unit,
        onDeleteSentence: () -> Unit,
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        fun makeKey(label: String, description: String, action: () -> Unit) = TextView(context).apply {
            text = label
            contentDescription = description
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(textColor())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(specialKeyColor())
                setStroke(dp(1), borderColor())
            }
            setOnClickListener { action() }
        }
        listOf(
            makeKey("Para", "Select paragraph", onSelectParagraph),
            makeKey("⇧◀", "Extend selection left", onExtendLeft),
            makeKey("⇧▶", "Extend selection right", onExtendRight),
            makeKey("⌫ Sent", "Delete previous sentence", onDeleteSentence),
        ).forEach { key ->
            row.addView(key, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), dp(1), dp(2), dp(1)) })
        }
        return row
    }
}
