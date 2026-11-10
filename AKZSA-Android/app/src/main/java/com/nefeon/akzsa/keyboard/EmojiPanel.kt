package com.nefeon.akzsa.keyboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nefeon.akzsa.R

object EmojiPanel {

    data class EmojiCategory(val label: String, val emojis: List<String>)

    val CATEGORIES = listOf(
        EmojiCategory("😊 Faces", listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃",
            "😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
            "🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫",
            "🤔","🤐","🤨","😐","😑","😶","😏","😒","🙄","😬",
            "🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢",
            "🤧","🥵","🥶","🥴","😵","🤯","🤠","🥳","🥸","😎",
            "🤓","🧐","😕","😟","🙁","☹️","😮","😯","😲","😳",
            "🥺","😦","😧","😨","😰","😥","😢","😭","😱","😖",
            "😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬",
            "😈","👿","💀","☠️","💩","🤡","👹","👺","👻","👾"
        )),
        EmojiCategory("👋 People", listOf(
            "👋","🤚","🖐️","✋","🖖","👌","🤌","🤏","✌️","🤞",
            "🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝️","👍",
            "👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🤝",
            "🙏","✍️","💅","🤳","💪","🦾","🦿","🦵","🦶","👂",
            "🦻","👃","🫀","🫁","🧠","🦷","🦴","👀","👁️","👅",
            "👄","💋","🫦","👶","🧒","👦","👧","🧑","👱","👨",
            "🧔","👩","🧓","👴","👵","🙍","🙎","🙅","🙆","💁",
            "🙋","🧏","🙇","🤦","🤷","👮","🕵️","💂","🥷","👷"
        )),
        EmojiCategory("❤️ Hearts", listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
            "❤️‍🔥","❤️‍🩹","❣️","💕","💞","💓","💗","💖","💘","💝",
            "💟","☮️","✝️","☪️","🕉️","☸️","✡️","🔯","🕎","☯️",
            "💯","💢","💥","💫","💦","💨","🕳️","💬","💭","💤"
        )),
        EmojiCategory("🎉 Party", listOf(
            "🎉","🎊","🎈","🎁","🎀","🎗️","🎟️","🎫","🏆","🥇",
            "🥈","🥉","🏅","🎖️","🏵️","🎪","🤹","🎭","🩰","🎨",
            "🎬","🎤","🎧","🎼","🎵","🎶","🎷","🎸","🎹","🎺",
            "🎻","🪕","🥁","🪘","🎲","♟️","🎯","🎳","🎮","🕹️"
        )),
        EmojiCategory("🐶 Animals", listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨",
            "🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐒",
            "🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗",
            "🐴","🦄","🐝","🐛","🦋","🐌","🐞","🐜","🦟","🦗",
            "🦂","🐢","🐍","🦎","🦖","🦕","🐙","🦑","🦐","🦞",
            "🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅"
        )),
        EmojiCategory("🍕 Food", listOf(
            "🍕","🍔","🍟","🌭","🌮","🌯","🥙","🧆","🥚","🍳",
            "🥘","🍲","🫕","🥣","🥗","🍿","🧈","🧂","🥫","🍱",
            "🍘","🍙","🍚","🍛","🍜","🍝","🍠","🍢","🍣","🍤",
            "🍥","🥮","🍡","🥟","🥠","🥡","🦪","🍦","🍧","🍨",
            "🍩","🍪","🎂","🍰","🧁","🥧","🍫","🍬","🍭","🍮",
            "☕","🍵","🧃","🥤","🧋","🍶","🍺","🍻","🥂","🍷"
        )),
        EmojiCategory("⚽ Sports", listOf(
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱",
            "🪀","🏓","🏸","🏒","🥍","🏏","🪃","🥅","⛳","🪁",
            "🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷","⛸️",
            "🥌","🎿","⛷️","🏂","🪂","🏋️","🤸","🤼","🤺","🤾"
        )),
        EmojiCategory("🚗 Travel", listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐",
            "🛻","🚚","🚛","🚜","🏍️","🛵","🚲","🛴","🛺","🚁",
            "🛸","🚀","✈️","🛶","⛵","🚢","🚂","🚆","🚇","🚈",
            "🚉","🚊","🚝","🚞","🚋","🚍","🏠","🏡","🏢","🏣"
        )),
        EmojiCategory("💡 Objects", listOf(
            "💡","🔦","🕯️","🪔","💻","⌨️","🖥️","🖨️","🖱️","🖲️",
            "📱","☎️","📞","📟","📠","📺","📻","🧭","⏱️","⏰",
            "📷","📸","📹","🎥","📽️","🎞️","📞","📡","🔋","🔌",
            "💾","💿","📀","🧮","📔","📒","📕","📗","📘","📙",
            "📚","📓","📃","📄","📑","🗒️","📊","📈","📉","🗂️",
            "🗃️","🗄️","🗑️","🔒","🔓","🔏","🔐","🔑","🗝️","🔨"
        )),
        EmojiCategory("🌍 Nature", listOf(
            "🌍","🌎","🌏","🌐","🗺️","🧭","🌋","🏔️","⛰️","🏕️",
            "🏖️","🏜️","🏝️","🏞️","🌅","🌄","🌠","🎇","🎆","🌇",
            "🌆","🏙️","🌃","🌌","🌉","🌁","⛅","🌤️","🌥️","☁️",
            "🌦️","🌧️","⛈️","🌩️","🌨️","❄️","☃️","⛄","🌬️","💨",
            "🌪️","🌫️","🌈","🌂","☂️","☔","⛱️","⚡","🔥","💧",
            "🌊","🌿","🍀","🍁","🍂","🍃","🌸","🌼","🌻","🌹"
        )),
        EmojiCategory("🇮🇳 India", listOf(
            "🇮🇳","🪔","🕌","⛩️","🛕","🪷","🌺","🥭","🍛","🫓",
            "🧆","🫖","🎊","🪘","🥁","💃","🕺","🧘","🤸","🎭",
            "🎪","🎨","🎬","🎤","🎵","🎶","📿","💎","👑","🏅",
            "🌙","⭐","🌟","✨","💫","🔱","☸️","🕉️","🙏","🤲"
        )),
        EmojiCategory("✅ Symbols", listOf(
            "✅","❌","⭕","🔴","🟠","🟡","🟢","🔵","🟣","⚫",
            "⚪","🟤","🔶","🔷","🔸","🔹","🔺","🔻","💠","🔘",
            "🔳","🔲","▪️","▫️","◾","◽","◼️","◻️","🔼","🔽",
            "⏩","⏪","⏫","⏬","▶️","⏸️","⏹️","⏺️","🔀","🔁",
            "🔂","🔃","🔄","🔙","🔚","🔛","🔜","🔝","🆗","🆕",
            "🆙","🆒","🆓","🆖","🆘","❓","❔","❕","❗","💯"
        )),
    )

    /**
     * Tabs: search, recents, favourites, then the categories. Tap inserts (with the chosen skin tone); long-press
     * toggles favourite. While [searching], typed keys fill [query] (routed by [TypingController]).
     */
    fun build(
        context: Context,
        dp: (Int) -> Int,
        textColor: () -> Int,
        keyColor: () -> Int,
        borderColor: () -> Int,
        accentColor: () -> Int,
        onAccentColor: () -> Int,
        searching: Boolean,
        query: String,
        remember: Boolean,
        onEmojiSelected: (String) -> Unit,
        onToggleSearch: () -> Unit,
        onStatus: (String) -> Unit,
        onClose: () -> Unit,
    ): LinearLayout {
        val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val tabScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val emojiScroll = ScrollView(context).apply { isFillViewport = true }
        val emojiGrid = GridLayout(context).apply {
            columnCount = 8
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        emojiScroll.addView(emojiGrid, LinearLayout.LayoutParams(-1, -2))

        val tabs: List<Pair<String, () -> List<String>>> =
            listOf<Pair<String, () -> List<String>>>(
                "🕘" to { EmojiStore.recents(context) },
                "⭐" to { EmojiStore.favourites(context) },
            ) + CATEGORIES.map { category -> Pair(category.label.split(" ").first(), { category.emojis }) }

        var selected = if (searching) -1 else if (EmojiStore.recents(context).isEmpty()) 2 else 0

        fun show(list: List<String>, emptyMessage: String) {
            emojiGrid.removeAllViews()
            if (list.isEmpty()) {
                emojiGrid.addView(TextView(context).apply {
                    text = emptyMessage
                    textSize = 12f
                    setTextColor(textColor())
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }, GridLayout.LayoutParams(GridLayout.spec(0, 1), GridLayout.spec(0, 8)))
                return
            }
            val tone = EmojiStore.tone(context)
            val sizeSp = EmojiStore.sizeSp(context)
            val cellDp = maxOf(40, sizeSp + 18)
            list.forEach { base ->
                val display = EmojiLogic.withSkinTone(base, tone)
                val cell = TextView(context).apply {
                    text = display
                    contentDescription = context.getString(R.string.cd_emoji_insert, display)
                    textSize = sizeSp.toFloat()
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        if (remember) EmojiStore.recordUse(context, base)
                        onEmojiSelected(display)
                    }
                    setOnLongClickListener {
                        val now = EmojiStore.toggleFavourite(context, base)
                        onStatus(if (now) "Added to favourites" else "Removed from favourites")
                        true
                    }
                }
                emojiGrid.addView(cell, GridLayout.LayoutParams().apply {
                    width = dp(cellDp); height = dp(cellDp)
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                })
            }
        }

        fun load() {
            when {
                searching -> show(
                    if (query.isBlank()) EmojiStore.recents(context) else EmojiLogic.search(query),
                    "No emoji found. Try another word.",
                )
                else -> show(tabs[selected].second(), if (selected == 1) "Long-press an emoji to add it here." else "Nothing here yet.")
            }
        }

        fun tabBackground(highlight: Boolean) = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(if (highlight) accentColor() else keyColor())
        }

        fun paintTabs() {
            for (i in 0 until tabRow.childCount) {
                val highlight = if (i == 0) searching else i - 1 == selected
                tabRow.getChildAt(i).background = tabBackground(highlight)
            }
        }

        fun addTab(label: String, description: String, onClick: () -> Unit) {
            val tab = TextView(context).apply {
                text = label
                contentDescription = description
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { onClick() }
            }
            tabRow.addView(tab, LinearLayout.LayoutParams(-2, dp(36)).apply { setMargins(dp(2), 0, dp(2), 0) })
        }

        addTab("🔍", "Search emoji") { onToggleSearch() }
        tabs.forEachIndexed { index, tab ->
            val description = when (index) {
                0 -> "Recent emoji"
                1 -> "Favourite emoji"
                else -> context.getString(R.string.cd_emoji_category, CATEGORIES[index - 2].label)
            }
            addTab(tab.first, description) {
                if (searching) { onToggleSearch(); return@addTab }
                selected = index
                load()
                paintTabs()
            }
        }
        load()
        paintTabs()

        fun pill(label: String, description: String, onClick: (View) -> Unit) = TextView(context).apply {
            text = label
            contentDescription = description
            textSize = 12f
            setTextColor(textColor())
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(keyColor())
                setStroke(dp(1), borderColor())
            }
            setOnClickListener { onClick(it) }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        topRow.addView(TextView(context).apply {
            text = if (searching) "🔍 " + query + "▏" else ""
            textSize = 14f
            setTextColor(textColor())
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))
        topRow.addView(pill(EmojiLogic.withSkinTone("👍", EmojiStore.tone(context)), "Skin tone") {
            EmojiStore.cycleTone(context)
            val tone = EmojiStore.tone(context)
            (it as TextView).text = EmojiLogic.withSkinTone("👍", tone)
            load()
        }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        topRow.addView(pill("Aa", "Emoji size") {
            EmojiStore.cycleSize(context)
            load()
        }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        topRow.addView(pill(context.getString(R.string.emoji_close), context.getString(R.string.cd_emoji_close)) { onClose() })

        tabScroll.addView(tabRow)
        panel.addView(topRow)
        panel.addView(tabScroll)
        panel.addView(emojiScroll, LinearLayout.LayoutParams(-1, dp(160)))
        return panel
    }
}
