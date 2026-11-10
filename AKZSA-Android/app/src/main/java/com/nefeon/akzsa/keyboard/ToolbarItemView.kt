package com.nefeon.akzsa.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.nefeon.akzsa.design.GraphiteSignal
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * One toolbar button. Every button uses the same vertical structure, so icons and labels line up by construction:
 *
 *     [ fixed icon box, glyph centred by its visual bounds ]
 *     [ fixed gap ]
 *     [ one label line, drawn on a shared baseline ]
 *
 * The whole view is the touch target (the toolbar gives it at least 48dp x 48dp); the pill background is drawn
 * smaller than the view so neighbouring cells never overlap. Nothing here depends on the icon or the label to
 * place the other one.
 */
internal class ToolbarItemView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = LABEL_TYPEFACE
        textAlign = Paint.Align.CENTER
    }
    private val body = RectF()

    private var style = KeyStyle.TOOL
    private var palette: GraphiteSignal.Palette? = null

    var label: String = ""
        set(value) { field = value; invalidate() }
    var icon: KeyIcon = KeyIcon.SPARKLE
        set(value) { field = value; invalidate() }
    var labelSizeSp: Float = ToolbarLayoutPolicy.MAX_LABEL_SP
        set(value) { field = value; invalidate() }

    init {
        isClickable = true
        isFocusable = true
    }

    fun bind(selected: Boolean, palette: GraphiteSignal.Palette) {
        this.palette = palette
        style = if (selected) KeyStyle.TOOL_ON else KeyStyle.TOOL
        isSelected = selected
        invalidate()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val pal = palette ?: return
        if (width <= 0 || height <= 0) return

        val inset = BG_INSET_DP * density
        body.set(inset, inset, width - inset, height - inset)
        val radius = min(CORNER_DP * density, body.height() / 2f)
        bgPaint.style = Paint.Style.FILL
        bgPaint.color = KeyColors.fill(style, pal, isPressed)
        canvas.drawRoundRect(body, radius, radius, bgPaint)

        // Shared vertical structure: identical for all seven items, so the baselines are identical too.
        val box = ToolbarLayoutPolicy.ICON_BOX_DP * density
        val gap = ToolbarLayoutPolicy.ICON_LABEL_GAP_DP * density
        labelPaint.textSize = spToPx(context, labelSizeSp)
        val fm = labelPaint.fontMetrics
        val labelHeight = fm.descent - fm.ascent
        val top = round(max(0f, (height - (box + gap + labelHeight)) / 2f))
        val cx = width / 2f

        // Icon: centred in its box by the glyph's visual bounds (not by its drawable grid).
        val iconPx = ToolbarLayoutPolicy.ICON_VISUAL_DP * density
        val scale = iconPx / 24f
        val (offX, offY) = KeyIcons.opticalOffset(icon)
        KeyIcons.draw(canvas, icon, cx + offX * scale, top + box / 2f + offY * scale, iconPx, KeyColors.icon(style, pal), iconPaint)

        // Label: one baseline, centred, squeezed (never clipped) if a very large font scale still overflows.
        val room = width - 2 * inset - 2 * density
        val natural = labelPaint.measureText(label)
        labelPaint.textScaleX = if (natural > room && natural > 0f) max(0.7f, room / natural) else 1f
        labelPaint.color = KeyColors.label(style, pal)
        canvas.drawText(label, cx, round(top + box + gap - fm.ascent), labelPaint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
    }

    override fun hasOverlappingRendering(): Boolean = false

    companion object {
        private const val BG_INSET_DP = 2f
        private const val CORNER_DP = 12f
        private val LABEL_TYPEFACE: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        private fun spToPx(context: Context, sp: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)

        /** Width in px of [text] in the toolbar label style at [sp]; used to size cells before the views exist. */
        fun labelWidthPx(context: Context, text: String, sp: Float): Float {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = LABEL_TYPEFACE; textSize = spToPx(context, sp) }
            return p.measureText(text)
        }
    }
}
