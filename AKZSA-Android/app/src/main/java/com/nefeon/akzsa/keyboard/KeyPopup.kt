package com.nefeon.akzsa.keyboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.nefeon.akzsa.design.GraphiteSignal

/**
 * The bubble above a pressed key: either a big preview of the character, or a row of accent choices while the
 * key is held. The popup never takes touches ([PopupWindow.isTouchable] = false), so the pressed key keeps
 * receiving the whole gesture and decides what to do on release.
 */
internal class KeyPopup(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    private var window: PopupWindow? = null
    private var cells: List<TextView> = emptyList()
    private var options: List<String> = emptyList()
    private var palette: GraphiteSignal.Palette? = null
    private var leftPx = 0f
    private var cellPx = 0f
    private var selected = -1

    val isShowingAccents: Boolean get() = options.isNotEmpty()

    fun showPreview(anchor: View, label: String, palette: GraphiteSignal.Palette) {
        dismiss()
        val width = maxOf(anchor.width, dp(46f))
        val height = dp(58f)
        val view = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(palette.textPrimary)
            background = bubble(palette.surfaceElevated, palette.borderStrong)
        }
        present(anchor, view, width, height)
    }

    fun showAccents(anchor: View, choices: List<String>, palette: GraphiteSignal.Palette) {
        dismiss()
        if (choices.isEmpty()) return
        this.palette = palette
        options = choices
        val cellW = dp(40f)
        val cellH = dp(48f)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = bubble(palette.surfaceElevated, palette.borderStrong)
            setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
        }
        cells = choices.map { choice ->
            TextView(context).apply {
                text = choice
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(palette.textPrimary)
            }.also { row.addView(it, LinearLayout.LayoutParams(cellW, cellH)) }
        }
        val totalW = cellW * choices.size + dp(4f)
        val screenW = context.resources.displayMetrics.widthPixels
        val desiredLeft = anchor.screenX() + anchor.width / 2 - totalW / 2
        val left = desiredLeft.coerceIn(0, maxOf(0, screenW - totalW))
        leftPx = (left + dp(2f)).toFloat()
        cellPx = cellW.toFloat()
        // Start on the cell nearest the pressed key so releasing straight away picks something sensible.
        selected = -1
        moveTo((anchor.screenX() + anchor.width / 2).toFloat())
        present(anchor, row, totalW, cellH + dp(4f), left)
    }

    /** Highlights the accent under [rawX] (screen coordinates). */
    fun moveTo(rawX: Float) {
        if (options.isEmpty()) return
        val index = KeyTouchPolicy.accentIndexAt(rawX, leftPx, cellPx, options.size)
        if (index == selected) return
        selected = index
        val p = palette ?: return
        cells.forEachIndexed { i, cell ->
            val on = i == index
            cell.setTextColor(if (on) p.onAccent else p.textPrimary)
            cell.background = if (on) GradientDrawable().apply {
                setColor(p.accent)
                cornerRadius = dp(8f).toFloat()
            } else null
        }
    }

    fun selectedOption(): String? = options.getOrNull(selected)

    fun dismiss() {
        window?.dismiss()
        window = null
        cells = emptyList()
        options = emptyList()
        selected = -1
    }

    private fun present(anchor: View, content: View, widthPx: Int, heightPx: Int, fixedLeft: Int? = null) {
        val screenW = context.resources.displayMetrics.widthPixels
        val left = fixedLeft ?: (anchor.screenX() + anchor.width / 2 - widthPx / 2).coerceIn(0, maxOf(0, screenW - widthPx))
        val top = maxOf(0, anchor.screenY() - heightPx - dp(2f))
        val popup = PopupWindow(content, widthPx, heightPx, false).apply {
            isTouchable = false
            isFocusable = false
            isClippingEnabled = false
            elevation = dp(6f).toFloat()
        }
        try {
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, left, top)
            window = popup
        } catch (_: RuntimeException) {
            // The input window can be torn down between touch events; a missing preview is harmless.
            window = null
        }
    }

    private fun bubble(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1f), stroke)
        cornerRadius = dp(10f).toFloat()
    }

    private fun dp(value: Float): Int = (value * density).toInt()

    private fun View.screenX(): Int = IntArray(2).also { getLocationOnScreen(it) }[0]
    private fun View.screenY(): Int = IntArray(2).also { getLocationOnScreen(it) }[1]
}
