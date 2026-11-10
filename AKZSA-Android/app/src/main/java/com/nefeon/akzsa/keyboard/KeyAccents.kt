package com.nefeon.akzsa.keyboard

import java.util.Locale

/**
 * Long-press alternatives for keys (accented letters and common punctuation). Pure Kotlin so it is unit-tested.
 * At most [MAX_OPTIONS] entries so the popup always fits a phone screen.
 */
internal object KeyAccents {
    const val MAX_OPTIONS = 7

    private val table: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "į"),
        "o" to listOf("ò", "ó", "ô", "ö", "œ", "ø", "õ"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "c" to listOf("ç", "ć", "č"),
        "n" to listOf("ñ", "ń"),
        "s" to listOf("ß", "ś", "š"),
        "y" to listOf("ÿ", "ý"),
        "z" to listOf("ž", "ź", "ż"),
        "l" to listOf("ł"),
        "." to listOf("…", "?", "!", ",", ":", ";"),
        "," to listOf("'", "\"", "-", "(", ")"),
    )

    /**
     * Options for the key currently showing [label] (upper- or lower-case). Empty when the key has none.
     * Upper-case labels give upper-case options, except where that would change the length (ß stays ß).
     */
    fun forLabel(label: String): List<String> {
        if (label.isEmpty()) return emptyList()
        val lower = label.lowercase(Locale.ROOT)
        val options = table[lower] ?: return emptyList()
        val upper = label != lower
        return options.take(MAX_OPTIONS).map { option ->
            if (!upper) option else option.uppercase(Locale.ROOT).takeIf { it.length == option.length } ?: option
        }
    }
}
