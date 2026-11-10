package com.nefeon.akzsa.keyboard

/** Pure emoji rules (recents, favourites, skin tones, keyword search); unit-tested, no Android types. */
internal object EmojiLogic {
    const val MAX_RECENTS = 32

    private val KEYWORDS: Map<String, List<String>> = mapOf(
        "smile" to listOf("😊", "😄", "🙂"), "happy" to listOf("😊", "😄", "🥳"), "laugh" to listOf("😂", "🤣", "😆"), "lol" to listOf("😂", "🤣"),
        "funny" to listOf("😂", "🤣"), "love" to listOf("❤️", "😍", "🥰"), "heart" to listOf("❤️", "💕", "💖"), "kiss" to listOf("😘", "💋"),
        "sad" to listOf("😢", "😞", "🥺"), "cry" to listOf("😭", "😢"), "angry" to listOf("😡", "😠", "🤬"), "cool" to listOf("😎", "🆒"),
        "thanks" to listOf("🙏", "😊"), "thank" to listOf("🙏", "😊"), "pray" to listOf("🙏"), "please" to listOf("🙏", "🥺"), "sorry" to listOf("🙏", "😔"),
        "ok" to listOf("👌", "👍"), "okay" to listOf("👌", "👍"), "yes" to listOf("👍", "✅"), "no" to listOf("👎", "❌"), "good" to listOf("👍", "😊"),
        "great" to listOf("👍", "🔥", "🙌"), "nice" to listOf("👍", "😊"), "bad" to listOf("👎", "😞"), "fire" to listOf("🔥"), "party" to listOf("🎉", "🥳", "🎊"),
        "celebrate" to listOf("🎉", "🥳"), "congrats" to listOf("🎉", "👏"), "congratulations" to listOf("🎉", "👏"), "birthday" to listOf("🎂", "🎉", "🎁"),
        "cake" to listOf("🎂", "🍰"), "gift" to listOf("🎁"), "sleep" to listOf("😴", "💤"), "tired" to listOf("😴", "🥱"), "sick" to listOf("🤒", "🤧"),
        "wink" to listOf("😉"), "think" to listOf("🤔", "💭"), "wow" to listOf("😮", "🤩"), "star" to listOf("⭐", "🌟"), "sun" to listOf("☀️", "🌞"),
        "moon" to listOf("🌙", "🌛"), "rain" to listOf("🌧️", "☔"), "snow" to listOf("❄️", "⛄"), "food" to listOf("🍕", "🍔", "🍟"), "pizza" to listOf("🍕"),
        "coffee" to listOf("☕"), "tea" to listOf("🍵", "☕"), "beer" to listOf("🍺"), "car" to listOf("🚗"), "phone" to listOf("📱", "☎️"), "money" to listOf("💰", "💵"),
        "home" to listOf("🏠", "🏡"), "dog" to listOf("🐶", "🐕"), "cat" to listOf("🐱", "🐈"), "india" to listOf("🇮🇳"), "flower" to listOf("🌸", "🌹", "🌼"),
        "clap" to listOf("👏"), "hello" to listOf("👋"), "hi" to listOf("👋"), "bye" to listOf("👋"), "strong" to listOf("💪"), "gym" to listOf("💪", "🏋️"),
        "check" to listOf("✅", "✔️"), "cross" to listOf("❌"), "warning" to listOf("⚠️"), "music" to listOf("🎵", "🎶"), "game" to listOf("🎮"), "book" to listOf("📚", "📖"),
        "idea" to listOf("💡"), "time" to listOf("⏰", "🕒"), "run" to listOf("🏃"), "football" to listOf("⚽"), "cricket" to listOf("🏏"), "baby" to listOf("👶"),
        "family" to listOf("👪"), "friend" to listOf("🤝", "👫"), "peace" to listOf("✌️", "☮️"), "ghost" to listOf("👻"), "skull" to listOf("💀"), "eyes" to listOf("👀"),
        "hundred" to listOf("💯"), "100" to listOf("💯"), "india flag" to listOf("🇮🇳"), "work" to listOf("💼", "🖥️"), "office" to listOf("🏢", "💼"),
    )

    /** Emoji that accept a skin-tone modifier (base forms, without the variation selector). */
    private val TONEABLE: Set<String> = ("👋🤚🖐✋🖖👌🤌🤏✌🤞🤟🤘🤙👈👉👆👇☝👍👎✊👊🤛🤜👏🙌👐🤲🙏✍💅🤳💪🦵🦶👂🦻👃👶🧒👦👧🧑👱👨🧔👩🧓👴👵🙍🙎🙅🙆💁🙋🧏🙇🤦🤷👮💂👷")
        .codePoints().toArray().map { String(Character.toChars(it)) }.toSet()

    /** [tone] 0 = default yellow, 1..5 = light to dark. Emoji that do not support tones are returned unchanged. */
    fun withSkinTone(emoji: String, tone: Int): String {
        if (tone !in 1..5) return emoji
        val base = emoji.removeSuffix("\uFE0F")
        if (base !in TONEABLE) return emoji
        return base + String(Character.toChars(0x1F3FA + tone))
    }

    /** Most-recent-first list without duplicates, capped at [max]. */
    fun pushRecent(list: List<String>, emoji: String, max: Int = MAX_RECENTS): List<String> =
        (listOf(emoji) + list.filter { it != emoji }).take(max)

    /** Adds [emoji] when absent, removes it when present. */
    fun toggle(list: List<String>, emoji: String): List<String> =
        if (emoji in list) list.filter { it != emoji } else listOf(emoji) + list

    fun encode(list: List<String>): String = list.joinToString("|")
    fun decode(raw: String?): List<String> = raw.orEmpty().split("|").filter { it.isNotEmpty() }

    /** Keyword search: exact keyword first, then keywords starting with the query. At most [limit] distinct emoji. */
    fun search(query: String, limit: Int = 40): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        KEYWORDS[q]?.let { out += it }
        for ((key, list) in KEYWORDS) if (key.startsWith(q) && key != q) out += list
        if (out.isEmpty()) for ((key, list) in KEYWORDS) if (key.contains(q)) out += list
        return out.take(limit)
    }

    /** One emoji for a fully typed [word] (exact keyword only, so suggestions stay quiet), or null. */
    fun forWord(word: String): String? = if (word.length >= 2) KEYWORDS[word.lowercase()]?.firstOrNull() else null
}
