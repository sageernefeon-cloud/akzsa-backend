package com.nefeon.akzsa.keyboard

import android.content.Context
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nefeon.akzsa.R

object ClipboardRow {

    /** Tap pastes, long-press pins or unpins, the small cross deletes one clip. */
    internal fun build(
        context: Context,
        dp: (Int) -> Int,
        textColor: () -> Int,
        keyColor: () -> Int,
        borderColor: () -> Int,
        accentColor: () -> Int,
        items: List<ClipEntry>,
        onPaste: (String) -> Unit,
        onTogglePin: (String) -> Unit,
        onDelete: (String) -> Unit,
        onClear: () -> Unit,
        searching: Boolean,
        query: String,
        filterLabel: String,
        onToggleSearch: () -> Unit,
        onCycleFilter: () -> Unit,
        onEdit: (String) -> Unit,
    ): LinearLayout? {
        if (items.isEmpty() && !searching && filterLabel == "All") return null

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        container.addView(TextView(context).apply {
            text = context.getString(R.string.clipboard_label) + " · long-press to pin"
            textSize = 11f
            setTextColor(textColor())
            setPadding(dp(2), 0, 0, dp(2))
        })

        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun pill(label: String, description: String, stroke: Int, fg: Int, padH: Int) = TextView(context).apply {
            text = label
            contentDescription = description
            textSize = 12f
            maxLines = 1
            setPadding(dp(padH), dp(6), dp(padH), dp(6))
            setTextColor(fg)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(keyColor())
                setStroke(dp(1), stroke)
            }
        }

        items.forEach { item ->
            val chip = pill(
                (if (item.pinned) "📌 " else "") + item.text.replace("\n", " ").take(40),
                context.getString(R.string.cd_clipboard_paste_item, item.text.take(60)),
                if (item.pinned) accentColor() else borderColor(), textColor(), 10,
            ).apply {
                setOnClickListener { onPaste(item.text) }
                setOnLongClickListener { onTogglePin(item.text); true }
            }
            row.addView(chip, LinearLayout.LayoutParams(-2, dp(36)).apply { setMargins(dp(2), 0, 0, 0) })
            val delete = pill("✕", "Delete this clipboard item", borderColor(), textColor(), 8)
                .apply { setOnClickListener { onDelete(item.text) } }
            row.addView(delete, LinearLayout.LayoutParams(-2, dp(36)).apply { setMargins(dp(1), 0, dp(1), 0) })
            val edit = pill("✎", "Move to the text field to edit", borderColor(), textColor(), 8)
                .apply { setOnClickListener { onEdit(item.text) } }
            row.addView(edit, LinearLayout.LayoutParams(-2, dp(36)).apply { setMargins(dp(1), 0, dp(4), 0) })
        }

        val clear = pill(context.getString(R.string.clipboard_clear), context.getString(R.string.cd_clipboard_clear), accentColor(), accentColor(), 10)
            .apply { setOnClickListener { onClear() } }
        row.addView(clear, LinearLayout.LayoutParams(-2, dp(36)).apply { setMargins(dp(2), 0, dp(2), 0) })

        val controls = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        controls.addView(pill(if (searching) "🔍 $query▏" else "🔍 Search", "Search clipboard", if (searching) accentColor() else borderColor(), textColor(), 10)
            .apply { setOnClickListener { onToggleSearch() } }, LinearLayout.LayoutParams(-2, dp(34)).apply { setMargins(dp(2), 0, dp(4), dp(2)) })
        controls.addView(pill("Show: $filterLabel", "Filter clipboard by type", borderColor(), textColor(), 10)
            .apply { setOnClickListener { onCycleFilter() } }, LinearLayout.LayoutParams(-2, dp(34)).apply { setMargins(dp(2), 0, dp(4), dp(2)) })
        if (items.isEmpty()) {
            row.addView(TextView(context).apply { text = "No clips match."; textSize = 12f; setTextColor(textColor()); setPadding(dp(8), 0, dp(8), 0) })
        }
        scroll.addView(row)
        container.addView(controls)
        container.addView(scroll)
        return container
    }
}
