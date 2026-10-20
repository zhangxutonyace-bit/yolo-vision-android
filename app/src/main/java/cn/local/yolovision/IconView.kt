package cn.local.yolovision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Small, consistent vector controls; no font glyphs or remote image dependencies. */
class IconView(context: Context, var icon: String, var tint: Int) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        val scale = minOf(width, height) / 24f
        canvas.translate((width - scale * 24) / 2, (height - scale * 24) / 2)
        canvas.scale(scale, scale)
        pen.color = tint
        pen.style = Paint.Style.STROKE
        fun path(vararg coords: Float) {
            val p = Path().apply { moveTo(coords[0], coords[1]); for (i in 2 until coords.size step 2) lineTo(coords[i], coords[i + 1]) }
            canvas.drawPath(p, pen)
        }
        when (icon) {
            "settings" -> {
                canvas.drawCircle(12f, 12f, 7.2f, pen)
                canvas.drawCircle(12f, 12f, 2.8f, pen)
                for (i in 0..7) {
                    val angle = i * Math.PI / 4
                    canvas.drawLine((12 + cos(angle) * 7.2).toFloat(), (12 + sin(angle) * 7.2).toFloat(), (12 + cos(angle) * 9.5).toFloat(), (12 + sin(angle) * 9.5).toFloat(), pen)
                }
            }
            "share" -> {
                path(7f, 7f, 3.5f, 7f, 3.5f, 21f, 20.5f, 21f, 20.5f, 7f, 17f, 7f)
                path(8f, 5f, 12f, 1f, 16f, 5f)
                path(12f, 1f, 12f, 14f)
            }
            "switch" -> {
                path(2f, 8f, 6f, 8f, 8f, 5f, 16f, 5f, 18f, 8f, 22f, 8f, 22f, 20f, 2f, 20f, 2f, 8f)
                canvas.drawArc(8f, 10f, 16f, 18f, 40f, 285f, false, pen)
                path(16f, 10f, 16f, 13f, 13f, 13f)
            }
            "pause" -> {
                pen.style = Paint.Style.FILL
                canvas.drawRoundRect(6f, 4f, 10f, 20f, 1.5f, 1.5f, pen)
                canvas.drawRoundRect(14f, 4f, 18f, 20f, 1.5f, 1.5f, pen)
            }
            "play" -> {
                pen.style = Paint.Style.FILL
                canvas.drawPath(Path().apply { moveTo(7f, 3f); lineTo(21f, 12f); lineTo(7f, 21f); close() }, pen)
            }
            "flash" -> {
                pen.style = Paint.Style.FILL
                canvas.drawPath(Path().apply { moveTo(14f, 1f); lineTo(4f, 14f); lineTo(11f, 14f); lineTo(9f, 23f); lineTo(21f, 9f); lineTo(13f, 9f); close() }, pen)
            }
            "camera" -> {
                path(2f, 7f, 7f, 7f, 9f, 4f, 15f, 4f, 17f, 7f, 22f, 7f, 22f, 21f, 2f, 21f, 2f, 7f)
                canvas.drawCircle(12f, 14f, 4f, pen)
            }
            "down" -> path(6f, 9f, 12f, 15f, 18f, 9f)
            "next" -> path(9f, 5f, 16f, 12f, 9f, 19f)
        }
        canvas.restore()
    }
}
