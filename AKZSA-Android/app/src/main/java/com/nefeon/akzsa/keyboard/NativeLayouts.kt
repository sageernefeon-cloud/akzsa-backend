package com.nefeon.akzsa.keyboard

import kotlin.math.ceil

/**
 * Native-script typing layouts for 11 languages. Pure data and rules, validated by tests.
 *
 * The Indic scripts follow the InScript idea: Unicode keeps the Indic blocks parallel, so every script uses the
 * Devanagari key set shifted into its own block, keeping only code points that are really assigned in that script
 * (for example Tamil has no aspirated consonants, so those keys simply do not appear). Arabic and Urdu have their own
 * letter sets. Each layout has two pages (letters + signs, then vowels + extras + digits) of four rows.
 */
object NativeLayouts {
    class Script(val code: String, val displayName: String, val blockStart: Int, val blockEnd: Int, val offset: Int)

    /** Devanagari is U+0900; the other Indic blocks are 0x80 apart (Tamil 0B80, Telugu 0C00, Kannada 0C80, Malayalam 0D00 ...). */
    val SCRIPTS: List<Script> = listOf(
        Script("hi", "Hindi", 0x0900, 0x097F, 0x0000),
        Script("mr", "Marathi", 0x0900, 0x097F, 0x0000),
        Script("bn", "Bengali", 0x0980, 0x09FF, 0x0080),
        Script("pa", "Punjabi", 0x0A00, 0x0A7F, 0x0100),
        Script("gu", "Gujarati", 0x0A80, 0x0AFF, 0x0180),
        Script("ta", "Tamil", 0x0B80, 0x0BFF, 0x0280),
        Script("te", "Telugu", 0x0C00, 0x0C7F, 0x0300),
        Script("kn", "Kannada", 0x0C80, 0x0CFF, 0x0380),
        Script("ml", "Malayalam", 0x0D00, 0x0D7F, 0x0400),
    )

    private val ARABIC_LETTERS = listOf("ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر", "ز", "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف", "ق", "ك", "ل", "م", "ن", "ه", "و", "ي")
    private val ARABIC_EXTRAS = listOf("ء", "آ", "أ", "إ", "ؤ", "ئ", "ة", "ى", "َ", "ُ", "ِ", "ّ", "ْ", "ً", "،", "؟", "؛",
        "٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
    private val URDU_LETTERS = listOf("ا", "ب", "پ", "ت", "ٹ", "ث", "ج", "چ", "ح", "خ", "د", "ڈ", "ذ", "ر", "ڑ", "ز", "ژ", "س", "ش", "ص", "ض", "ط", "ظ",
        "ع", "غ", "ف", "ق", "ک", "گ", "ل", "م", "ن", "ں", "و", "ہ", "ھ", "ی", "ے")
    private val URDU_EXTRAS = listOf("ء", "آ", "أ", "ئ", "ؤ", "ۃ", "ۓ", "َ", "ُ", "ِ", "ّ", "ْ", "ٰ", "ً", "،", "؟", "؛", "۔",
        "۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")

    /** All language codes with a native typing layout. */
    val CODES: List<String> = SCRIPTS.map { it.code } + listOf("ar", "ur")

    fun isSupported(code: String) = code in CODES

    /** Splits [keys] into [rows] rows of nearly equal length (each at most 10 keys for the sizes used here). */
    fun distribute(keys: List<String>, rows: Int = 4): List<List<String>> {
        val out = ArrayList<List<String>>(rows)
        var index = 0
        for (r in 0 until rows) {
            val remainingRows = rows - r
            val take = ceil((keys.size - index) / remainingRows.toFloat()).toInt()
            out += keys.subList(index, index + take)
            index += take
        }
        return out
    }

    private fun shifted(key: String, script: Script): String? {
        val sb = StringBuilder()
        for (cp in key.codePoints().toArray()) {
            val mapped = if (cp in 0x0964..0x0965) cp                    // shared danda / double danda
            else if (cp in 0x0900..0x097F) cp + script.offset else cp
            if (mapped !in script.blockStart..script.blockEnd && mapped !in 0x0964..0x0965) return null
            if (!Character.isDefined(mapped) || Character.getType(mapped) == Character.UNASSIGNED.toInt()) return null
            sb.appendCodePoint(mapped)
        }
        return sb.toString()
    }

    private fun indic(script: Script, page: Int): List<String> {
        val source = if (page == 0) DevanagariLayout.page0 else DevanagariLayout.page1
        if (script.code == "hi" || script.code == "mr") return source.flatten()
        return source.flatten().mapNotNull { shifted(it, script) }.distinct()
    }

    /** Two pages of four rows each for [code] (falls back to Hindi for an unknown code). */
    fun pages(code: String): List<List<List<String>>> {
        val flat: Pair<List<String>, List<String>> = when (code) {
            "ar" -> ARABIC_LETTERS to ARABIC_EXTRAS
            "ur" -> URDU_LETTERS to URDU_EXTRAS
            else -> {
                val script = SCRIPTS.firstOrNull { it.code == code } ?: SCRIPTS.first()
                indic(script, 0) to indic(script, 1)
            }
        }
        return listOf(distribute(flat.first), distribute(flat.second))
    }

    fun rows(code: String, page: Int): List<List<String>> = pages(code)[page.coerceIn(0, 1)]

    /** Label of the page-toggle key: the first key of the OTHER page, so it looks like the script. */
    fun toggleLabel(code: String, currentPage: Int): String {
        val other = pages(code)[if (currentPage == 0) 1 else 0]
        return other.firstOrNull { it.isNotEmpty() }?.firstOrNull()?.toString() ?: "…"
    }
}
