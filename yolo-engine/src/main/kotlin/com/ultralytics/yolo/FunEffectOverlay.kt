package com.ultralytics.yolo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

enum class HeadEffect(val animated: Boolean = false) {
    NONE, CAT_EARS, RABBIT_CLOUD, FRUIT_MASK, KITCHEN_ORBIT(true), COMIC_MASK, CLASSIC_CROWN;

    companion object {
        fun restore(stored: String?, legacyCatEars: Boolean): HeadEffect = when (stored) {
            null -> if (legacyCatEars) CAT_EARS else NONE
            else -> entries.firstOrNull { it.name == stored } ?: NONE
        }
    }
}

/** A display style for one detection class. The logo is supplied by the application. */
data class FunEffectOverlayStyle(
    val classIndex: Int,
    val displayName: String,
    val rounded: Boolean,
    val logo: Bitmap,
    val headEffect: HeadEffect = HeadEffect.NONE,
    val showBox: Boolean = true
)

/** Draws the same target effect in the live overlay, frozen frames, and settings preview. */
class FunEffectOverlayRenderer(private val density: Float, scaledDensity: Float) {
    private val blue = Color.rgb(21, 101, 255)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = blue
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * scaledDensity
        typeface = Typeface.DEFAULT_BOLD
    }
    private val metrics = labelPaint.fontMetrics
    private val logoBounds = RectF()
    private val maskArtwork by lazy { HeadMaskArtwork() }
    private val accessoryArtwork by lazy { HeadAccessoryArtwork() }
    // The ear shapes use the distance between the eyes as their drawing unit.
    private val outerEar = Path().apply {
        moveTo(-1.17f, -.78f)
        cubicTo(-1.34f, -1.19f, -1.36f, -1.72f, -1.24f, -1.94f)
        quadTo(-1.19f, -2.01f, -1.08f, -1.94f)
        cubicTo(-.71f, -1.72f, -.39f, -1.26f, -.18f, -.86f)
        quadTo(-.64f, -.68f, -1.17f, -.78f)
        close()
    }
    private val innerEar = Path().apply {
        moveTo(-1.05f, -.91f)
        quadTo(-1.22f, -1.38f, -1.13f, -1.69f)
        quadTo(-.81f, -1.53f, -.47f, -.93f)
        quadTo(-.76f, -.83f, -1.05f, -.91f)
        close()
    }
    private val earFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(195, 165, 255) }
    private val earOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(115, 73, 176)
        style = Paint.Style.STROKE
        strokeWidth = .068f
        strokeJoin = Paint.Join.ROUND
    }
    private val earInnerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 178, 215) }

    fun draw(canvas: Canvas, box: RectF, style: FunEffectOverlayStyle, viewportWidth: Float, viewportHeight: Float) {
        if (viewportWidth <= 0f || viewportHeight <= 0f ||
            !box.left.isFinite() || !box.top.isFinite() || !box.right.isFinite() || !box.bottom.isFinite() ||
            box.isEmpty || box.right <= 0f || box.bottom <= 0f || box.left >= viewportWidth || box.top >= viewportHeight
        ) return

        if (style.showBox) {
            // Keep the mapped box aligned to the subject even when it extends outside the preview.
            if (style.rounded) {
                val radius = minOf(10f * density, box.width() / 2f, box.height() / 2f)
                canvas.drawRoundRect(box, radius, radius, border)
            } else {
                canvas.drawRect(box, border)
            }
        }

        val pad = 7f * density
        val gap = 5f * density
        val iconSize = 16f * density
        val labelHeight = maxOf(26f * density, metrics.descent - metrics.ascent + 6f * density)
        if (viewportHeight < labelHeight || viewportWidth < 2f * pad + iconSize) return

        val maxTextWidth = (viewportWidth - 2f * pad - iconSize - gap).coerceAtLeast(0f)
        val label = TextUtils.ellipsize(style.displayName, labelPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
        val labelWidth = (2f * pad + iconSize + if (label.isEmpty()) 0f else gap + labelPaint.measureText(label))
            .coerceAtMost(viewportWidth)
        val left = box.left.coerceIn(0f, viewportWidth - labelWidth)
        val badgeEdge = if (style.headEffect != HeadEffect.NONE) box.bottom else box.top
        val top = (badgeEdge - labelHeight + 6f * density).coerceIn(0f, viewportHeight - labelHeight)
        val centerY = top + labelHeight / 2f
        val radius = 7f * density
        canvas.drawRoundRect(left, top, left + labelWidth, top + labelHeight, radius, radius, badge)

        val logo = style.logo
        val logoScale = iconSize / maxOf(logo.width, logo.height)
        val logoWidth = logo.width * logoScale
        val logoHeight = logo.height * logoScale
        val logoLeft = left + pad + (iconSize - logoWidth) / 2f
        logoBounds.set(logoLeft, centerY - logoHeight / 2f, logoLeft + logoWidth, centerY + logoHeight / 2f)
        canvas.drawBitmap(logo, null, logoBounds, logoPaint)

        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, left + pad + iconSize + gap, baseline, labelPaint)
    }

    fun drawHeadEffect(
        canvas: Canvas,
        effect: HeadEffect,
        eyeA: PointF,
        eyeB: PointF,
        nose: PointF? = null,
        timeMs: Long = SystemClock.uptimeMillis()
    ): Boolean {
        if (effect == HeadEffect.NONE) return false
        val dx = eyeB.x - eyeA.x
        val dy = eyeB.y - eyeA.y
        val span = hypot(dx, dy)
        if (!span.isFinite() || span < 4f * density) return false

        val centerX = (eyeA.x + eyeB.x) / 2f
        val centerY = (eyeA.y + eyeB.y) / 2f
        // Eye order changes under mirroring. The nose, when visible, tells us which way is down.
        val noseProjection = nose?.let { (it.y - centerY) * dx - (it.x - centerX) * dy }
        val reverse = if (noseProjection != null && abs(noseProjection) > span * span * .1f) {
            noseProjection < 0f
        } else {
            dx < 0f
        }
        val direction = if (reverse) -1f else 1f
        val angle = Math.toDegrees(atan2((dy * direction).toDouble(), (dx * direction).toDouble())).toFloat()

        val saveCount = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(angle)
        canvas.scale(span, span)
        when (effect) {
            HeadEffect.NONE -> Unit
            HeadEffect.CAT_EARS -> {
                drawEar(canvas)
                canvas.scale(-1f, 1f)
                drawEar(canvas)
            }
            HeadEffect.RABBIT_CLOUD -> maskArtwork.drawRabbit(canvas)
            HeadEffect.FRUIT_MASK -> maskArtwork.drawFruitMask(canvas)
            HeadEffect.KITCHEN_ORBIT -> accessoryArtwork.drawKitchenOrbit(canvas, timeMs)
            HeadEffect.COMIC_MASK -> maskArtwork.drawComicMask(canvas)
            HeadEffect.CLASSIC_CROWN -> accessoryArtwork.drawClassicCrown(canvas)
        }
        canvas.restoreToCount(saveCount)
        return true
    }

    private fun drawEar(canvas: Canvas) {
        canvas.drawPath(outerEar, earFill)
        canvas.drawPath(innerEar, earInnerFill)
        canvas.drawPath(outerEar, earOutline)
    }
}
