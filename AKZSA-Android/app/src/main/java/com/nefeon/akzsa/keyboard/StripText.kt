package com.nefeon.akzsa.keyboard

/** Text shown in the keyboard's input strip: the tail of what the user has typed before the cursor. Pure Kotlin, unit-tested. */
internal object StripText {
    const val MAX = 48

    fun compose(before: CharSequence?): String {
        val flat = before?.toString().orEmpty().toString().replace(Regex("\\s+"), " ").trimStart()
        if (flat.isEmpty()) return ""
        return if (flat.length <= MAX) flat else "…" + flat.takeLast(MAX).trimStart()
    }
}
