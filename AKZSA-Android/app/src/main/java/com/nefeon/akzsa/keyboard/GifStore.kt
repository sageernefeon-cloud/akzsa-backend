package com.nefeon.akzsa.keyboard

import android.content.Context

/** Recent and favourite GIFs / stickers, stored privately on the device. */
internal object GifStore {
    private const val PREFS = "akzsa_gifs"
    private const val MAX_RECENTS = 24

    fun encode(list: List<GifPanel.GifResult>): String =
        list.joinToString("\n") { listOf(it.url, it.previewUrl, it.title).joinToString("\t") { f -> f.replace('\t', ' ').replace('\n', ' ') } }

    fun decode(raw: String?): List<GifPanel.GifResult> = raw.orEmpty().split("\n").mapNotNull { line ->
        val f = line.split("\t")
        if (f.size < 3 || f[0].isBlank() || !f[0].startsWith("https://")) null else GifPanel.GifResult(f[0], f[1], f[2])
    }

    fun pushRecent(list: List<GifPanel.GifResult>, gif: GifPanel.GifResult, max: Int = MAX_RECENTS) =
        (listOf(gif) + list.filter { it.url != gif.url }).take(max)

    fun toggle(list: List<GifPanel.GifResult>, gif: GifPanel.GifResult) =
        if (list.any { it.url == gif.url }) list.filter { it.url != gif.url } else listOf(gif) + list

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun recents(c: Context) = decode(prefs(c).getString("recents", ""))
    fun favourites(c: Context) = decode(prefs(c).getString("favourites", ""))
    fun recordUse(c: Context, gif: GifPanel.GifResult) { prefs(c).edit().putString("recents", encode(pushRecent(recents(c), gif))).apply() }
    fun toggleFavourite(c: Context, gif: GifPanel.GifResult): Boolean {
        val next = toggle(favourites(c), gif)
        prefs(c).edit().putString("favourites", encode(next)).apply()
        return next.any { it.url == gif.url }
    }
}
