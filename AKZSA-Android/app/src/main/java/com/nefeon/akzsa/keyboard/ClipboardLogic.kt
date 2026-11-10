package com.nefeon.akzsa.keyboard

/** One remembered clip. [pinned] clips never expire and are never evicted by newer clips. */
internal data class ClipEntry(val text: String, val pinned: Boolean, val savedAtMs: Long)

/** Pure clipboard rules (no Android types): privacy filter, ordering, expiry, storage format, smart extraction. */
internal object ClipboardLogic {
    const val MAX_ITEMS = 30
    const val MAX_CHARS = 500

    private val OTP_OR_PIN = Regex("^\\d{4,8}$")
    private val CARD_LIKE = Regex("^(?:\\d[ -]?){13,19}$")
    private val SECRET_WORDS = Regex("(?i)\\b(otp|one[- ]time|verification code|passcode|password|cvv|cvc|pin)\\b.*\\d{4,}|\\d{4,}.*\\b(otp|one[- ]time|verification code|passcode|cvv|cvc)\\b")

    /** Passwords, one-time codes, PINs and card numbers are never remembered. */
    fun looksSensitive(text: String): Boolean {
        val t = text.trim()
        return OTP_OR_PIN.matches(t) || CARD_LIKE.matches(t) || SECRET_WORDS.containsMatchIn(t)
    }

    /** Newest first; re-copying moves a clip to the front and keeps its pin. Sensitive or blank text is ignored. */
    fun add(entries: List<ClipEntry>, text: String, now: Long): List<ClipEntry> {
        val clean = text.trim().take(MAX_CHARS)
        if (clean.isBlank() || looksSensitive(clean)) return entries
        val existing = entries.firstOrNull { it.text == clean }
        val next = listOf(ClipEntry(clean, existing?.pinned ?: false, now)) + entries.filter { it.text != clean }
        if (next.size <= MAX_ITEMS) return next
        val dropAt = next.indexOfLast { !it.pinned }
        return if (dropAt < 0) next.take(MAX_ITEMS) else next.filterIndexed { i, _ -> i != dropAt }
    }

    fun togglePin(entries: List<ClipEntry>, text: String): List<ClipEntry> =
        entries.map { if (it.text == text) it.copy(pinned = !it.pinned) else it }

    fun remove(entries: List<ClipEntry>, text: String): List<ClipEntry> = entries.filter { it.text != text }

    /** Drops unpinned clips older than [retentionMs]. */
    fun prune(entries: List<ClipEntry>, now: Long, retentionMs: Long): List<ClipEntry> =
        entries.filter { it.pinned || now - it.savedAtMs <= retentionMs }

    /** Display order: pinned clips first, each group newest first. */
    fun ordered(entries: List<ClipEntry>): List<ClipEntry> = entries.filter { it.pinned } + entries.filter { !it.pinned }

    fun search(entries: List<ClipEntry>, query: String): List<ClipEntry> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) entries else entries.filter { it.text.lowercase().contains(q) }
    }

    // ---- storage format: one clip per line "pinned|savedAtMs|escaped text" ----

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "")
    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                sb.append(if (s[i + 1] == 'n') '\n' else s[i + 1]); i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    fun encode(entries: List<ClipEntry>): String =
        entries.joinToString("\n") { "${if (it.pinned) 1 else 0}|${it.savedAtMs}|${escape(it.text)}" }

    fun decode(raw: String?): List<ClipEntry> = raw.orEmpty().split("\n").mapNotNull { line ->
        val first = line.indexOf('|'); if (first < 0) return@mapNotNull null
        val second = line.indexOf('|', first + 1); if (second < 0) return@mapNotNull null
        val ts = line.substring(first + 1, second).toLongOrNull() ?: return@mapNotNull null
        val text = unescape(line.substring(second + 1))
        if (text.isBlank()) null else ClipEntry(text, line.substring(0, first) == "1", ts)
    }

    // ---- categories ----

    val FILTERS = listOf("All", "Pinned", "Links", "Emails", "Phones")

    fun category(text: String): String {
        val found = extract(text)
        return when {
            found.any { it.kind == "url" } -> "Links"
            found.any { it.kind == "email" } -> "Emails"
            found.any { it.kind == "phone" } -> "Phones"
            else -> "Text"
        }
    }

    fun filter(entries: List<ClipEntry>, name: String): List<ClipEntry> = when (name) {
        "Pinned" -> entries.filter { it.pinned }
        "Links", "Emails", "Phones" -> entries.filter { category(it.text) == name }
        else -> entries
    }

    // ---- smart extraction ----

    data class Extract(val kind: String, val value: String)

    private val URL = Regex("(?i)\\b(?:https?://|www\\.)[^\\s]+")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?<![\\w@])\\+?\\d[\\d\\s-]{8,16}\\d(?![\\w@])")
    private val DATE = Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b")

    /** URLs, email addresses, phone numbers and dates found in [text], in that order, without duplicates. */
    fun extract(text: String): List<Extract> {
        val out = LinkedHashSet<Extract>()
        URL.findAll(text).forEach { out += Extract("url", it.value.trimEnd('.', ',', ';', ')', '!', '?')) }
        EMAIL.findAll(text).forEach { out += Extract("email", it.value.trimEnd('.')) }
        PHONE.findAll(text).forEach { m ->
            val digits = m.value.count { it.isDigit() }
            if (digits in 10..13) out += Extract("phone", m.value.trim())
        }
        DATE.findAll(text).forEach { out += Extract("date", it.value) }
        return out.toList()
    }
}
