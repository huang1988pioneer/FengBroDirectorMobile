package com.fengbro.director.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.fengbro.director.core.export.ExportPlan
import com.fengbro.director.core.export.TitleSegment
import com.fengbro.director.core.model.EditorProject
import kotlin.math.max

@UnstableApi
class TimelineOverlay(private val plan: ExportPlan) : BitmapOverlay() {
    private var bitmap: Bitmap? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(180, 0, 0, 0)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
    }
    private val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(199, 255, 255, 255)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val watermarkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(140, 0, 0, 0)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val w = plan.width.coerceAtLeast(2)
        val h = plan.height.coerceAtLeast(2)
        val bmp = bitmap?.takeIf { it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it }
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val t = presentationTimeUs / 1_000_000.0
        val active = plan.titles.lastOrNull { t >= it.start && t < it.end }
        if (active != null) drawTitle(canvas, active, w.toFloat(), h.toFloat(), t)
        if (plan.watermark) drawWatermark(canvas, w.toFloat(), h.toFloat())
        return bmp
    }

    private fun drawTitle(canvas: Canvas, title: TitleSegment, w: Float, h: Float, t: Double) {
        val size = max(28f, title.fontSize.toFloat() * (h / plan.height.coerceAtLeast(1)))
        fill.textSize = size
        stroke.textSize = size
        stroke.strokeWidth = max(2f, size / 16f)
        fill.color = parseColor(title.color)
        val text = title.text.replace('\n', ' ').trim().ifBlank { return }
        val words = title.words
        val y = if (title.caption) h * 0.88f else h * 0.5f
        if (title.caption) {
            val width = fill.measureText(text)
            val pad = size * 0.45f
            canvas.drawRoundRect(
                RectF(w / 2f - width / 2f - pad, y - size, w / 2f + width / 2f + pad, y + size * 0.4f),
                12f,
                12f,
                box,
            )
        }
        if (!words.isNullOrEmpty()) {
            val local = t - title.start
            drawKaraoke(canvas, words, local, w / 2f, y)
        } else {
            canvas.drawText(text, w / 2f, y, stroke)
            canvas.drawText(text, w / 2f, y, fill)
        }
    }

    private fun drawKaraoke(
        canvas: Canvas,
        words: List<com.fengbro.director.core.model.LyricWord>,
        local: Double,
        cx: Float,
        y: Float,
    ) {
        val full = words.joinToString("") { it.text }
        val total = fill.measureText(full)
        var x = cx - total / 2f
        fill.textAlign = Paint.Align.LEFT
        stroke.textAlign = Paint.Align.LEFT
        for (word in words) {
            val on = local >= word.start
            fill.color = if (on) Color.parseColor("#3EC8D4") else Color.WHITE
            canvas.drawText(word.text, x, y, stroke)
            canvas.drawText(word.text, x, y, fill)
            x += fill.measureText(word.text)
        }
        fill.textAlign = Paint.Align.CENTER
        stroke.textAlign = Paint.Align.CENTER
        fill.color = Color.WHITE
    }

    private fun drawWatermark(canvas: Canvas, w: Float, h: Float) {
        val size = max(18f, h / 42f)
        watermarkPaint.textSize = size
        watermarkStroke.textSize = size
        watermarkStroke.strokeWidth = max(1f, size / 18f)
        val margin = max(22f, h * 0.034f)
        val gap = size * 1.28f
        val lines = listOf(
            EditorProject.WATERMARK_LINE_1,
            EditorProject.WATERMARK_LINE_2,
            EditorProject.WATERMARK_LINE_3,
        )
        for (i in lines.indices) {
            val fromBottom = lines.lastIndex - i
            val y = h - margin - fromBottom * gap
            canvas.drawText(lines[i], w - margin, y, watermarkStroke)
            canvas.drawText(lines[i], w - margin, y, watermarkPaint)
        }
    }

    private fun parseColor(raw: String): Int = try {
        val s = raw.trim()
        when {
            s.startsWith("#") && (s.length == 7 || s.length == 9) -> Color.parseColor(s)
            else -> Color.WHITE
        }
    } catch (_: Exception) {
        Color.WHITE
    }
}
