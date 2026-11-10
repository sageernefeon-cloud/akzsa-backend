package com.nefeon.akzsa.keyboard

import android.util.Log

/**
 * Keyboard failures that used to be swallowed silently. Logs only the exception class and the place, never
 * message text (it can contain typed or spoken content). `Log.w` stays in release builds on purpose.
 */
internal object KeyboardLog {
    fun w(where: String, e: Throwable) {
        Log.w("AKZSA", "$where: ${e.javaClass.simpleName}")
    }
}
