package com.nefeon.akzsa.keyboard

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.widget.TextView
import com.nefeon.akzsa.design.GraphiteSignal

/** Visual role of a key. Decides fill, pressed fill, border and label colour. */
internal enum class KeyStyle {
    NORMAL, SPECIAL, ACCENT,
    /** The central microphone: a surface key with a teal voice ring. */
    VOICE,
    /** Flat toolbar button (no key fill); [TOOL_ON] = its panel is open / selected. */
    TOOL, TOOL_ON,
}

/**
 * Pure colour rules for [KeyView] (no Android types besides the palette's ARGB ints, so they are unit-tested).
 *
 * Accent keys are solid indigo fills with an on-accent label (no outline needed); the mic key carries a teal ring.
 */
internal object KeyColors {
    fun fill(style: KeyStyle, palette: GraphiteSignal.Palette, pressed: Boolean): Int = when (style) {
        KeyStyle.ACCENT -> if (pressed) palette.accentPressed else palette.accent
        KeyStyle.SPECIAL, KeyStyle.TOOL_ON -> if (pressed) palette.secondarySurfacePressed else palette.secondarySurface
        KeyStyle.TOOL -> if (pressed) palette.secondarySurfacePressed else palette.keyboardBackground
        KeyStyle.NORMAL, KeyStyle.VOICE -> if (pressed) palette.surfacePressed else palette.surface
    }

    fun border(style: KeyStyle, palette: GraphiteSignal.Palette): Int = when (style) {
        KeyStyle.VOICE -> palette.voice
        KeyStyle.ACCENT -> palette.accent
        else -> palette.border
    }

    fun borderWidthDp(style: KeyStyle): Int = when (style) {
        KeyStyle.VOICE -> 2
        KeyStyle.ACCENT, KeyStyle.TOOL, KeyStyle.TOOL_ON -> 0
        else -> 1
    }

    fun label(style: KeyStyle, palette: GraphiteSignal.Palette): Int =
        if (style == KeyStyle.ACCENT) palette.onAccent else palette.textPrimary

    /** Colour of a drawn icon (never used for text, so the 3:1 non-text rule applies). */
    fun icon(style: KeyStyle, palette: GraphiteSignal.Palette): Int = when (style) {
        KeyStyle.ACCENT -> palette.onAccent
        KeyStyle.VOICE -> palette.voice
        KeyStyle.TOOL_ON -> palette.accent
        else -> palette.textPrimary
    }
}

/**
 * One keyboard key. Draws its own rounded background (optional shadow, fill, border) in [onDraw] with a single
 * reused [Paint] instead of building and swapping a GradientDrawable on every state change; the label is then
 * drawn by [TextView] on top, so text shaping (Devanagari, emoji) and accessibility behave exactly as before.
 *
 * The pressed state animates the fill colour over [PRESS_ANIM_MS] (a ValueAnimator) rather than swapping it.
 * Nothing is allocated while drawing.
 */
internal class KeyView(context: Context) : TextView(context) {
    private val density = context.resources.displayMetrics.density
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val shadowRect = RectF()
    private val strokeRect = RectF()

    private var style = KeyStyle.NORMAL
    private var palette: GraphiteSignal.Palette? = null
    private var cornerPx = 0f
    private var strokePx = 0f
    private var shadowEnabled = false
    /** Visual inset on every side: the view fills its whole grid cell (no dead zones between keys) but draws smaller. */
    private var insetPx = 0f

    private var fillColor = 0
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** When set, the glyph is drawn instead of (or above) the text label. */
    var icon: KeyIcon? = null
        set(value) { field = value; invalidate() }
    var iconSizeDp = 22f
    /** Toolbar buttons: icon on top, small caption below. */
    var captionBelow = false
    private var borderColor = 0
    private var fillAnimator: ValueAnimator? = null

    init {
        gravity = Gravity.CENTER
        maxLines = 1
        isClickable = true
    }

    /**
     * Applies a style. [shadow] draws the optional elevation look: a second rounded rect, lower alpha, offset
     * [SHADOW_OFFSET_DP] down, behind the key. [SHADOW_RESERVE_DP] is kept free at the bottom of the view so the
     * shadow is never clipped (the label stays centred on the key body because the same amount is added as
     * bottom padding).
     */
    fun bind(style: KeyStyle, palette: GraphiteSignal.Palette, cornerRadiusDp: Int, shadow: Boolean, insetDp: Int = 0) {
        this.style = style
        this.palette = palette
        cornerPx = cornerRadiusDp * density
        strokePx = KeyColors.borderWidthDp(style) * density
        borderColor = KeyColors.border(style, palette)
        shadowEnabled = shadow
        insetPx = insetDp * density
        setTextColor(KeyColors.label(style, palette))
        val inset = insetPx.toInt()
        setPadding(inset + dpToPx(1f), inset, inset + dpToPx(1f), inset + if (shadow) dpToPx(SHADOW_RESERVE_DP) else 0)
        fillAnimator?.cancel()
        fillColor = KeyColors.fill(style, palette, isPressed)
        invalidate()
    }

    override fun setPressed(pressed: Boolean) {
        val wasPressed = isPressed
        super.setPressed(pressed)
        // super's constructor may call this before bind(); nothing to animate until a palette exists.
        val p = palette ?: return
        if (wasPressed != pressed) {
            animateFillTo(KeyColors.fill(style, p, pressed))
            // Tiny press/release scale: feels physical without costing frames.
            animate().scaleX(if (pressed) 0.95f else 1f).scaleY(if (pressed) 0.95f else 1f).setDuration(PRESS_ANIM_MS).start()
        }
    }

    private fun animateFillTo(target: Int) {
        fillAnimator?.cancel()
        if (fillColor == target) return
        if (!isAttachedToWindow || !isShown) {
            fillColor = target
            invalidate()
            return
        }
        val from = fillColor
        fillAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, target).apply {
            duration = PRESS_ANIM_MS
            addUpdateListener {
                fillColor = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        fillAnimator?.cancel()
        fillAnimator = null
        palette?.let { fillColor = KeyColors.fill(style, it, false) }
        super.onDetachedFromWindow()
    }

    /** Small secondary character in the top-right corner (the digit above Q-P). */
    var hintText: String? = null
        set(value) { field = value; invalidate() }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }

    private fun drawHint(canvas: Canvas) {
        val hint = hintText ?: return
        val pal = palette ?: return
        val d = resources.displayMetrics.density
        hintPaint.color = pal.textSecondary
        hintPaint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 9f, resources.displayMetrics)
        canvas.drawText(hint, width - insetPx - 4f * d, insetPx + 11f * d, hintPaint)
    }

    override fun onDraw(canvas: Canvas) {
        if (palette != null && width > 0 && height > 0) {
            val reserve = if (shadowEnabled) dpToPx(SHADOW_RESERVE_DP).toFloat() else 0f
            body.set(insetPx, insetPx, width - insetPx, height - reserve - insetPx)
            val radius = minOf(cornerPx, body.height() / 2f)

            if (shadowEnabled) {
                shadowRect.set(body)
                shadowRect.offset(0f, SHADOW_OFFSET_DP * density)
                keyPaint.style = Paint.Style.FILL
                keyPaint.color = SHADOW_COLOR
                canvas.drawRoundRect(shadowRect, radius, radius, keyPaint)
            }

            keyPaint.style = Paint.Style.FILL
            keyPaint.color = fillColor
            canvas.drawRoundRect(body, radius, radius, keyPaint)

            if (strokePx > 0f) {
                val half = strokePx / 2f
                strokeRect.set(body)
                strokeRect.inset(half, half)
                keyPaint.style = Paint.Style.STROKE
                keyPaint.strokeWidth = strokePx
                keyPaint.color = borderColor
                val strokeRadius = maxOf(0f, radius - half)
                canvas.drawRoundRect(strokeRect, strokeRadius, strokeRadius, keyPaint)
            }
        }
        val glyph = icon
        val pal = palette
        if (pal != null && glyph != null && width > 0 && height > 0) {
            val reserve = if (shadowEnabled) dpToPx(SHADOW_RESERVE_DP).toFloat() else 0f
            val bodyH = height - reserve - 2 * insetPx
            val cx = width / 2f
            val bodyCy = insetPx + bodyH / 2f
            when (glyph) {
                KeyIcon.WAVE_LEFT, KeyIcon.WAVE_RIGHT -> {
                    val color = (pal.voice and 0x00FFFFFF) or (0x99 shl 24)
                    KeyIcons.drawWave(canvas, cx, bodyCy, width - 2 * insetPx - 8 * density, bodyH * 0.5f, color, iconPaint, glyph == KeyIcon.WAVE_LEFT)
                }
                else -> {
                    val size = iconSizeDp * density
                    val cy = if (captionBelow) insetPx + bodyH * 0.38f else bodyCy
                    KeyIcons.draw(canvas, glyph, cx, cy, size, KeyColors.icon(style, pal), iconPaint)
                }
            }
        }
        super.onDraw(canvas)   // the label (empty for pure-icon keys)
        drawHint(canvas)
    }

    override fun hasOverlappingRendering(): Boolean = false

    private fun dpToPx(dp: Float): Int = (dp * density).toInt()

    companion object {
        const val PRESS_ANIM_MS = 80L
        /** Space kept free under the key body for the shadow shape. */
        const val SHADOW_RESERVE_DP = 2f
        /** How far the shadow rect is shifted down from the key body. */
        const val SHADOW_OFFSET_DP = 1f
        /** Black at ~20% alpha: lower alpha than the key fill, visible on light themes, subtle on dark ones. */
        const val SHADOW_COLOR = 0x33000000
    }
}
