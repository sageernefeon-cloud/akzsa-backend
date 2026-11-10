package com.nefeon.akzsa.keyboard

import android.content.ClipboardManager
import android.content.Context

/**
 * History of recently copied text.
 *
 * Android only exposes the CURRENT clip, so the keyboard has to remember earlier ones itself.
 * Privacy rules: by default nothing is written to disk and the list dies with the keyboard process. When the user
 * turns on "Remember clipboard" the list is stored privately on the device and expires after the retention time
 * (pinned clips excepted). Clips the source app marks as sensitive, copies made while a protected field is focused,
 * and text that looks like a password, OTP, PIN or card number are never kept. Incognito keeps nothing at all.
 */
object ClipboardHistory {
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"   // ClipDescription.EXTRA_IS_SENSITIVE (API 33)
    private const val PREFS = "akzsa_clipboard"
    private const val KEY = "items"

    private var entries: List<ClipEntry> = emptyList()
    private var manager: ClipboardManager? = null
    private var appContext: Context? = null
    private var persist = false
    private var incognito = false
    private var retentionMs = 24L * 3_600_000L
    private var protectedField: () -> Boolean = { false }
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { captureCurrent() }

    @Synchronized
    fun start(context: Context, isProtectedField: () -> Boolean) {
        appContext = context.applicationContext
        protectedField = isProtectedField
        if (manager != null) return
        val cm = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager = cm
        try { cm.addPrimaryClipChangedListener(listener) } catch (_: Exception) { manager = null }
    }

    @Synchronized
    fun stop() {
        try { manager?.removePrimaryClipChangedListener(listener) } catch (_: Exception) { /* already gone */ }
        manager = null
        entries = emptyList()          // memory only; a remembered list stays on disk when the user enabled it
    }

    /** Applies the user's settings. Cheap to call on every render. */
    @Synchronized
    fun configure(context: Context, persistEnabled: Boolean, retentionHours: Int, incognitoMode: Boolean) {
        appContext = context.applicationContext
        retentionMs = retentionHours.coerceIn(1, 24 * 30) * 3_600_000L
        incognito = incognitoMode
        val shouldPersist = persistEnabled && !incognitoMode
        if (shouldPersist != persist) {
            persist = shouldPersist
            if (persist) {
                val stored = ClipboardLogic.decode(prefs()?.getString(KEY, ""))
                entries = entries + stored.filter { s -> entries.none { it.text == s.text } }
            } else {
                prefs()?.edit()?.remove(KEY)?.apply()
            }
        }
        if (incognito) entries = emptyList()
        prune()
    }

    /** Call when the keyboard opens, to catch a copy made while the listener could not run. */
    fun captureCurrent() {
        if (protectedField() || incognito) return
        val cm = manager ?: return
        try {
            val clip = cm.primaryClip ?: return
            if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true) return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            add(text)
        } catch (_: Exception) { /* clipboard unavailable right now */ }
    }

    @Synchronized
    fun add(text: String) {
        if (incognito) return
        entries = ClipboardLogic.add(entries, text, System.currentTimeMillis())
        save()
    }

    /** Newest first (the order the rest of the keyboard relies on). */
    @Synchronized fun recent(max: Int = ClipboardLogic.MAX_ITEMS): List<String> = entries.take(max).map { it.text }

    /** Pinned clips first, then newest first. */
    @Synchronized internal fun ordered(): List<ClipEntry> { prune(); return ClipboardLogic.ordered(entries) }

    @Synchronized fun togglePin(text: String) { entries = ClipboardLogic.togglePin(entries, text); save() }
    @Synchronized fun remove(text: String) { entries = ClipboardLogic.remove(entries, text); save() }
    @Synchronized fun clear() { entries = emptyList(); save() }

    private fun prune() {
        val next = ClipboardLogic.prune(entries, System.currentTimeMillis(), retentionMs)
        if (next.size != entries.size) { entries = next; save() }
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save() {
        if (!persist) return
        prefs()?.edit()?.putString(KEY, ClipboardLogic.encode(entries))?.apply()
    }
}
