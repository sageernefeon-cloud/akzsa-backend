package com.nefeon.akzsa.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.nefeon.akzsa.design.GraphiteSignal
import kotlin.math.abs
import kotlin.math.sin

/** Small reusable view builders shared by every keyboard panel (AI Workspace, Languages, Snippets, voice, chips). */
internal object PanelUi {
    fun rounded(fill: Int, stroke: Int, radiusPx: Float, strokePx: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(fill)
        if (strokePx > 0) setStroke(strokePx, stroke)
    }

    /** A pill-shaped chip. Selected chips use the action colour; others a plain key surface. */
    fun chip(context: Context, text: String, selected: Boolean, p: GraphiteSignal.Palette, dp: (Int) -> Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 13f
            maxLines = 1
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            minHeight = dp(36)
            setTextColor(if (selected) p.onAccent else p.textPrimary)
            background = rounded(if (selected) p.accent else p.surface, p.border, dp(18).toFloat(), if (selected) 0 else dp(1))
            isClickable = true
            contentDescription = text
            setOnClickListener { onClick() }
        }

    fun card(context: Context, p: GraphiteSignal.Palette, dp: (Int) -> Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = rounded(p.surface, p.border, dp(14).toFloat(), dp(1))
    }

    fun label(context: Context, text: String, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(context).apply {
        this.text = text
        textSize = sp
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    /** A rounded read-only text box ("Before" / "After"). */
    fun textBox(context: Context, text: String, p: GraphiteSignal.Palette, dp: (Int) -> Int, maxLines: Int = 3): TextView = TextView(context).apply {
        this.text = text
        textSize = 14f
        this.maxLines = maxLines
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTextColor(p.textPrimary)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = rounded(p.secondarySurface, p.secondarySurface, dp(10).toFloat(), 0)
    }
}

/** A view that only draws one [KeyIcon]. */
internal class IconView(context: Context, private val icon: KeyIcon, private val color: Int, private val sizeDp: Float = 20f) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = context.resources.displayMetrics.density
    override fun onMeasure(w: Int, h: Int) {
        val px = (sizeDp * density).toInt()
        setMeasuredDimension(px, px)
    }
    override fun onDraw(canvas: Canvas) {
        KeyIcons.draw(canvas, icon, width / 2f, height / 2f, sizeDp * density, color, paint)
    }
}

/**
 * Animated listening waveform. Bars follow [level] (0..1) when the speech engine reports a sound level;
 * otherwise they breathe gently so the person can see that the microphone is live.
 */
internal class WaveView(context: Context, private val color: Int, private val level: () -> Float, private val idleOnly: Boolean = false) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = this@WaveView.color }
    private val rect = android.graphics.RectF()
    private var phase = 0f
    private var smoothed = 0.25f
    private var running = false
    private val bars = 25

    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = true; postInvalidateOnAnimation() }
    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val target = if (idleOnly) 0.18f else (0.22f + 0.78f * level().coerceIn(0f, 1f))
        smoothed += (target - smoothed) * 0.15f
        phase += 0.16f
        val gap = width / bars.toFloat()
        val barW = gap * 0.5f
        val mid = height / 2f
        for (i in 0 until bars) {
            val envelope = 1f - abs(i - bars / 2f) / (bars / 2f) * 0.75f
            val wobble = 0.55f + 0.45f * sin(phase + i * 0.55f)
            val h = (height * smoothed * envelope * wobble).coerceAtLeast(barW)
            val x = i * gap + (gap - barW) / 2f
            rect.set(x, mid - h / 2f, x + barW, mid + h / 2f)
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint)
        }
        if (running) postInvalidateOnAnimation()
    }
}
