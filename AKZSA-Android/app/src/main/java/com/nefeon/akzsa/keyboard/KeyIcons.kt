package com.nefeon.akzsa.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Vector-style glyphs drawn on a Canvas (no bitmaps, no extra dependencies). Every icon is designed on a 24x24 grid. */
internal enum class KeyIcon {
    SPARKLE, CLIPBOARD, EMOJI, GIF, SNIPPETS, LANGUAGE, SETTINGS, PLUS, SEND, ENTER,
    SHIFT, CAPS, BACKSPACE, CHEVRON_DOWN, KEYBOARD, MIC, STOP, WAVE_LEFT, WAVE_RIGHT, CLOSE, CHECK,
}

internal object KeyIcons {
    private val path = Path()
    private val rect = RectF()

    /** Heights (0..1) of the bars in the subtle waveform beside the microphone. */
    private val WAVE = floatArrayOf(0.30f, 0.55f, 0.85f, 0.55f, 0.95f, 0.50f, 0.35f)

    /**
     * Visual bounds (left, top, right, bottom, stroke excluded) of the toolbar glyphs on the 24x24 grid.
     * Some glyphs are not drawn symmetrically inside the grid, so centring the grid would shift them.
     */
    private fun visualBounds(icon: KeyIcon): FloatArray = when (icon) {
        KeyIcon.SPARKLE -> floatArrayOf(2f, 2f, 22f, 21f)
        KeyIcon.CLIPBOARD -> floatArrayOf(5f, 2.5f, 19f, 21f)
        KeyIcon.EMOJI -> floatArrayOf(3f, 3f, 21f, 21f)
        KeyIcon.GIF -> floatArrayOf(2.5f, 5.5f, 21.5f, 18.5f)
        KeyIcon.SNIPPETS -> floatArrayOf(6f, 3f, 19f, 21f)
        KeyIcon.LANGUAGE -> floatArrayOf(3f, 3f, 21f, 21f)
        KeyIcon.SETTINGS -> floatArrayOf(2.4f, 2.4f, 21.6f, 21.6f)
        else -> floatArrayOf(0f, 0f, 24f, 24f)
    }

    /** Shift (grid units) that moves the glyph's visual centre onto the centre of its 24x24 box. */
    fun opticalOffset(icon: KeyIcon): Pair<Float, Float> {
        val b = visualBounds(icon)
        return Pair(12f - (b[0] + b[2]) / 2f, 12f - (b[1] + b[3]) / 2f)
    }

    fun draw(canvas: Canvas, icon: KeyIcon, cx: Float, cy: Float, size: Float, color: Int, paint: Paint) {
        if (icon == KeyIcon.WAVE_LEFT || icon == KeyIcon.WAVE_RIGHT) return   // sized by the key, see drawWave
        val save = canvas.save()
        canvas.translate(cx - size / 2f, cy - size / 2f)
        val s = size / 24f
        canvas.scale(s, s)
        paint.color = color
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 1.9f
        stroke(paint)
        when (icon) {
            KeyIcon.SPARKLE -> {
                fill(paint)
                path.reset()
                path.moveTo(11f, 3f); path.cubicTo(11.9f, 8.2f, 14.8f, 11.1f, 20f, 12f)
                path.cubicTo(14.8f, 12.9f, 11.9f, 15.8f, 11f, 21f)
                path.cubicTo(10.1f, 15.8f, 7.2f, 12.9f, 2f, 12f)
                path.cubicTo(7.2f, 11.1f, 10.1f, 8.2f, 11f, 3f); path.close()
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(19f, 2f); path.lineTo(19.7f, 4.3f); path.lineTo(22f, 5f); path.lineTo(19.7f, 5.7f)
                path.lineTo(19f, 8f); path.lineTo(18.3f, 5.7f); path.lineTo(16f, 5f); path.lineTo(18.3f, 4.3f); path.close()
                canvas.drawPath(path, paint)
            }
            KeyIcon.CLIPBOARD -> {
                rect.set(5f, 4.5f, 19f, 21f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                rect.set(9f, 2.5f, 15f, 6.5f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
                canvas.drawLine(8.5f, 12f, 15.5f, 12f, paint)
                canvas.drawLine(8.5f, 16f, 13.5f, 16f, paint)
            }
            KeyIcon.EMOJI -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                fill(paint)
                canvas.drawCircle(9f, 10f, 1.1f, paint)
                canvas.drawCircle(15f, 10f, 1.1f, paint)
                stroke(paint)
                rect.set(7.5f, 7.5f, 16.5f, 16.5f)
                canvas.drawArc(rect, 25f, 130f, false, paint)
            }
            KeyIcon.GIF -> {
                rect.set(2.5f, 5.5f, 21.5f, 18.5f); canvas.drawRoundRect(rect, 3.5f, 3.5f, paint)
                fill(paint)
                paint.textSize = 7.2f
                paint.textAlign = Paint.Align.CENTER
                paint.isFakeBoldText = true
                canvas.drawText("GIF", 12f, 14.6f, paint)
                paint.isFakeBoldText = false
                paint.textAlign = Paint.Align.LEFT
            }
            KeyIcon.SNIPPETS -> {
                path.reset()
                path.moveTo(6f, 3f); path.lineTo(14f, 3f); path.lineTo(19f, 8f); path.lineTo(19f, 21f); path.lineTo(6f, 21f); path.close()
                canvas.drawPath(path, paint)
                path.reset(); path.moveTo(14f, 3f); path.lineTo(14f, 8f); path.lineTo(19f, 8f)
                canvas.drawPath(path, paint)
                canvas.drawLine(9f, 13f, 16f, 13f, paint)
                canvas.drawLine(9f, 17f, 14f, 17f, paint)
            }
            KeyIcon.LANGUAGE -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                rect.set(8f, 3f, 16f, 21f); canvas.drawOval(rect, paint)
                canvas.drawLine(3f, 12f, 21f, 12f, paint)
            }
            KeyIcon.SETTINGS -> {
                canvas.drawCircle(12f, 12f, 3f, paint)
                canvas.drawCircle(12f, 12f, 6.6f, paint)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x1 = (12 + 7.3 * cos(a)).toFloat(); val y1 = (12 + 7.3 * sin(a)).toFloat()
                    val x2 = (12 + 9.6 * cos(a)).toFloat(); val y2 = (12 + 9.6 * sin(a)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
            }
            KeyIcon.PLUS -> {
                canvas.drawLine(12f, 5f, 12f, 19f, paint)
                canvas.drawLine(5f, 12f, 19f, 12f, paint)
            }
            KeyIcon.SEND -> {
                fill(paint)
                path.reset()
                path.moveTo(3f, 11.5f); path.lineTo(21f, 3.5f); path.lineTo(15.5f, 20.5f); path.lineTo(12f, 13f); path.close()
                canvas.drawPath(path, paint)
            }
            KeyIcon.ENTER -> {
                path.reset(); path.moveTo(20f, 5f); path.lineTo(20f, 12f); path.lineTo(5f, 12f)
                canvas.drawPath(path, paint)
                path.reset(); path.moveTo(10f, 7f); path.lineTo(5f, 12f); path.lineTo(10f, 17f)
                canvas.drawPath(path, paint)
            }
            KeyIcon.SHIFT, KeyIcon.CAPS -> {
                if (icon == KeyIcon.CAPS) fill(paint)
                path.reset()
                path.moveTo(12f, 4f); path.lineTo(4f, 13f); path.lineTo(9f, 13f); path.lineTo(9f, 20f)
                path.lineTo(15f, 20f); path.lineTo(15f, 13f); path.lineTo(20f, 13f); path.close()
                canvas.drawPath(path, paint)
            }
            KeyIcon.BACKSPACE -> {
                path.reset()
                path.moveTo(9f, 5.5f); path.lineTo(20f, 5.5f); path.lineTo(20f, 18.5f); path.lineTo(9f, 18.5f)
                path.lineTo(3f, 12f); path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(12.5f, 9.5f, 16.5f, 14.5f, paint)
                canvas.drawLine(16.5f, 9.5f, 12.5f, 14.5f, paint)
            }
            KeyIcon.CHEVRON_DOWN -> {
                path.reset(); path.moveTo(6f, 9f); path.lineTo(12f, 15f); path.lineTo(18f, 9f)
                canvas.drawPath(path, paint)
            }
            KeyIcon.KEYBOARD -> {
                rect.set(2.5f, 5.5f, 21.5f, 18.5f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
                fill(paint)
                for (i in 0 until 4) canvas.drawCircle(7f + i * 3.33f, 9.5f, 0.9f, paint)
                for (i in 0 until 4) canvas.drawCircle(7f + i * 3.33f, 12.5f, 0.9f, paint)
                stroke(paint)
                canvas.drawLine(7.5f, 15.6f, 16.5f, 15.6f, paint)
            }
            KeyIcon.MIC -> {
                rect.set(8.8f, 2.8f, 15.2f, 14.2f); canvas.drawRoundRect(rect, 3.2f, 3.2f, paint)
                rect.set(5.5f, 5.5f, 18.5f, 18.5f); canvas.drawArc(rect, 20f, 140f, false, paint)
                canvas.drawLine(12f, 18.5f, 12f, 21.5f, paint)
                canvas.drawLine(8.5f, 21.5f, 15.5f, 21.5f, paint)
            }
            KeyIcon.STOP -> {
                fill(paint)
                rect.set(6.5f, 6.5f, 17.5f, 17.5f); canvas.drawRoundRect(rect, 2.5f, 2.5f, paint)
            }
            KeyIcon.CLOSE -> {
                canvas.drawLine(6f, 6f, 18f, 18f, paint)
                canvas.drawLine(18f, 6f, 6f, 18f, paint)
            }
            KeyIcon.CHECK -> {
                path.reset(); path.moveTo(5f, 12.5f); path.lineTo(10f, 17.5f); path.lineTo(19f, 7f)
                canvas.drawPath(path, paint)
            }
            KeyIcon.WAVE_LEFT, KeyIcon.WAVE_RIGHT -> Unit
        }
        stroke(paint)
        paint.strokeWidth = 0f
        canvas.restoreToCount(save)
    }

    /** Subtle bars beside the mic. [towardRight] = tallest bars sit on the right edge (the left-hand zone). */
    fun drawWave(canvas: Canvas, cx: Float, cy: Float, width: Float, maxHeight: Float, color: Int, paint: Paint, towardRight: Boolean) {
        val n = WAVE.size
        val barW = (width / (n * 2f - 1f)).coerceIn(2f, 6f)
        val total = barW * (n * 2f - 1f)
        var x = cx - total / 2f
        paint.style = Paint.Style.FILL
        paint.color = color
        for (i in 0 until n) {
            val level = if (towardRight) WAVE[i] else WAVE[n - 1 - i]
            val h = maxHeight * level
            rect.set(x, cy - h / 2f, x + barW, cy + h / 2f)
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint)
            x += barW * 2f
        }
    }

    private fun stroke(p: Paint) { p.style = Paint.Style.STROKE }
    private fun fill(p: Paint) { p.style = Paint.Style.FILL }
}
