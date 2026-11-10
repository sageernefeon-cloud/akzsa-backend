package com.nefeon.akzsa.keyboard

import android.content.Context

/** Persists emoji recents, favourites, skin tone and size on the device only (SharedPreferences). */
internal object EmojiStore {
    private const val PREFS = "akzsa_emoji"
    val SIZES = intArrayOf(18, 22, 28)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recents(c: Context): List<String> = EmojiLogic.decode(prefs(c).getString("recents", ""))
    fun favourites(c: Context): List<String> = EmojiLogic.decode(prefs(c).getString("favourites", ""))

    fun recordUse(c: Context, emoji: String) {
        prefs(c).edit().putString("recents", EmojiLogic.encode(EmojiLogic.pushRecent(recents(c), emoji))).apply()
    }

    /** Returns true when [emoji] is now a favourite. */
    fun toggleFavourite(c: Context, emoji: String): Boolean {
        val next = EmojiLogic.toggle(favourites(c), emoji)
        prefs(c).edit().putString("favourites", EmojiLogic.encode(next)).apply()
        return emoji in next
    }

    fun tone(c: Context): Int = prefs(c).getInt("tone", 0).coerceIn(0, 5)
    fun cycleTone(c: Context): Int { val n = (tone(c) + 1) % 6; prefs(c).edit().putInt("tone", n).apply(); return n }

    fun sizeSp(c: Context): Int = SIZES[prefs(c).getInt("size", 1).coerceIn(0, SIZES.size - 1)]
    fun cycleSize(c: Context) { val n = (prefs(c).getInt("size", 1) + 1) % SIZES.size; prefs(c).edit().putInt("size", n).apply() }

    fun clearRecents(c: Context) { prefs(c).edit().remove("recents").apply() }
}
