package com.ultralytics.yolo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Original artwork in eye-distance units. The face anchor and rotation are applied by the caller. */
internal class HeadAccessoryArtwork {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val orbit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = .023f
        color = Color.argb(130, 255, 205, 117)
    }

    private val pepper = Path().apply {
        moveTo(.29f, -.36f)
        cubicTo(.56f, -.30f, .60f, .02f, .40f, .20f)
        cubicTo(.16f, .45f, -.23f, .48f, -.49f, .34f)
        quadTo(-.55f, .29f, -.46f, .27f)
        cubicTo(-.21f, .25f, -.14f, .09f, -.08f, -.13f)
        cubicTo(-.03f, -.31f, .10f, -.41f, .29f, -.36f)
        close()
    }
    private val pepperStem = Path().apply {
        moveTo(.18f, -.32f)
        quadTo(.15f, -.46f, .34f, -.53f)
        quadTo(.44f, -.57f, .49f, -.49f)
        quadTo(.30f, -.44f, .32f, -.31f)
        close()
    }
    private val pepperGlint = Path().apply {
        moveTo(.33f, -.21f)
        quadTo(.45f, -.04f, .28f, .11f)
        quadTo(.15f, .23f, .04f, .24f)
    }
    private val spatulaHandle = Path().apply {
        moveTo(-.083f, -.11f)
        lineTo(.083f, -.11f)
        lineTo(.108f, .47f)
        quadTo(.106f, .56f, 0f, .56f)
        quadTo(-.106f, .56f, -.108f, .47f)
        close()
    }
    private val spatulaHead = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        moveTo(-.20f, -.51f)
        quadTo(-.25f, -.51f, -.25f, -.43f)
        lineTo(-.19f, -.16f)
        quadTo(-.17f, -.09f, -.09f, -.09f)
        lineTo(.09f, -.09f)
        quadTo(.17f, -.09f, .19f, -.16f)
        lineTo(.25f, -.43f)
        quadTo(.25f, -.51f, .20f, -.51f)
        close()
        addRoundRect(-.139f, -.424f, -.071f, -.223f, .027f, .027f, Path.Direction.CW)
        addRoundRect(-.034f, -.424f, .034f, -.223f, .027f, .027f, Path.Direction.CW)
        addRoundRect(.071f, -.424f, .139f, -.223f, .027f, .027f, Path.Direction.CW)
    }
    private val flame = Path().apply {
        moveTo(-.21f, -.07f)
        quadTo(-.19f, -.40f, .10f, -.57f)
        cubicTo(.05f, -.39f, .28f, -.32f, .24f, -.12f)
        quadTo(.33f, -.15f, .38f, -.23f)
        cubicTo(.56f, .06f, .39f, .42f, .15f, .47f)
        cubicTo(-.17f, .52f, -.40f, .36f, -.38f, .11f)
        quadTo(-.37f, -.03f, -.31f, -.15f)
        quadTo(-.27f, -.05f, -.21f, -.07f)
        close()
    }
    private val flameCore = Path().apply {
        moveTo(.10f, -.18f)
        cubicTo(.05f, .00f, .25f, .12f, .21f, .29f)
        cubicTo(.18f, .46f, -.06f, .45f, -.15f, .33f)
        quadTo(-.24f, .18f, -.08f, .04f)
        quadTo(-.07f, .18f, 0f, .16f)
        quadTo(-.01f, .00f, .10f, -.18f)
        close()
    }
    private val crown = Path().apply {
        moveTo(-1.25f, -.88f)
        cubicTo(-1.40f, -1.01f, -1.44f, -1.29f, -1.51f, -1.42f)
        cubicTo(-1.27f, -1.34f, -1.11f, -1.48f, -1.08f, -1.66f)
        quadTo(-.80f, -1.42f, -.56f, -1.60f)
        quadTo(-.40f, -1.85f, 0f, -2.03f)
        quadTo(.40f, -1.85f, .56f, -1.60f)
        quadTo(.80f, -1.42f, 1.08f, -1.66f)
        cubicTo(1.11f, -1.48f, 1.27f, -1.34f, 1.51f, -1.42f)
        cubicTo(1.44f, -1.29f, 1.40f, -1.01f, 1.25f, -.88f)
        quadTo(0f, -1.18f, -1.25f, -.88f)
        close()
    }
    private val crownBand = Path().apply {
        moveTo(-1.25f, -.88f)
        quadTo(0f, -1.18f, 1.25f, -.88f)
        lineTo(1.12f, -.69f)
        quadTo(0f, -.98f, -1.12f, -.69f)
        close()
    }
    private val crownInlay = Path().apply {
        moveTo(-.30f, -1.42f)
        quadTo(-.27f, -1.60f, 0f, -1.85f)
        quadTo(.27f, -1.60f, .30f, -1.42f)
        quadTo(0f, -1.18f, -.30f, -1.42f)
        close()
    }
    private val crownScroll = Path().apply {
        moveTo(-1.20f, -1.23f)
        cubicTo(-1.17f, -1.54f, -.94f, -1.20f, -.73f, -1.36f)
        quadTo(-.63f, -1.40f, -.57f, -1.54f)
    }

    private val chiliRed = Color.rgb(240, 72, 61)
    private val chiliOutline = Color.rgb(129, 29, 43)
    private val leafGreen = Color.rgb(92, 208, 106)
    private val leafOutline = Color.rgb(30, 109, 65)
    private val wood = Color.rgb(239, 172, 93)
    private val woodOutline = Color.rgb(120, 69, 39)
    private val steel = Color.rgb(212, 239, 248)
    private val steelOutline = Color.rgb(54, 92, 115)
    private val warmOrange = Color.rgb(255, 135, 61)
    private val flameOutline = Color.rgb(158, 51, 42)
    private val warmYellow = Color.rgb(255, 226, 113)
    private val darkGold = Color.rgb(115, 70, 32)
    private val gold = Color.rgb(255, 205, 109)
    private val goldLight = Color.rgb(255, 238, 175)
    private val jade = Color.rgb(75, 179, 156)
    private val jadeLight = Color.rgb(154, 237, 206)

    fun drawKitchenOrbit(canvas: Canvas, timeMs: Long) {
        val orbitCenterY = -1.70f
        val radiusX = 1.98f
        val radiusY = .43f
        canvas.drawOval(-radiusX, orbitCenterY - radiusY, radiusX, orbitCenterY + radiusY, orbit)
        val turn = (timeMs % 2520L) * (2.0 * PI / 2520.0)

        // Paint the far arc before the near arc when two silhouettes overlap in projection.
        for (pass in 0..1) {
            for (icon in 0..2) {
                val angle = turn + icon * (2.0 * PI / 3.0)
                val depth = sin(angle).toFloat()
                if ((depth >= 0f) != (pass == 1)) continue
                val saved = canvas.save()
                canvas.translate(radiusX * cos(angle).toFloat(), orbitCenterY + radiusY * depth)
                val scale = .73f + .10f * depth
                canvas.rotate(when (icon) {
                    0 -> 9f * depth
                    1 -> -31f + 8f * depth
                    else -> -6f * depth
                })
                canvas.scale(scale, scale)
                when (icon) {
                    0 -> {
                        outlined(canvas, pepperStem, leafGreen, leafOutline, .055f)
                        outlined(canvas, pepper, chiliRed, chiliOutline, .062f)
                        drawStroke(canvas, pepperGlint, Color.rgb(255, 180, 149), .045f)
                    }
                    1 -> {
                        outlined(canvas, spatulaHandle, wood, woodOutline, .055f)
                        outlined(canvas, spatulaHead, steel, steelOutline, .040f)
                    }
                    else -> {
                        outlined(canvas, flame, warmOrange, flameOutline, .060f)
                        fill.color = warmYellow
                        canvas.drawPath(flameCore, fill)
                    }
                }
                canvas.restoreToCount(saved)
            }
        }
    }

    fun drawClassicCrown(canvas: Canvas) {
        outlined(canvas, crown, jade, darkGold, .065f)
        outlined(canvas, crownBand, gold, darkGold, .055f)
        outlined(canvas, crownInlay, jadeLight, darkGold, .042f)
        drawStroke(canvas, crownScroll, goldLight, .038f)
        val mirrored = canvas.save()
        canvas.scale(-1f, 1f)
        drawStroke(canvas, crownScroll, goldLight, .038f)
        canvas.restoreToCount(mirrored)

        fill.color = goldLight
        canvas.drawCircle(0f, -2.01f, .091f, fill)
        canvas.drawCircle(-.38f, -1.08f, .054f, fill)
        canvas.drawCircle(.38f, -1.08f, .054f, fill)
        stroke.color = darkGold
        stroke.strokeWidth = .041f
        canvas.drawLine(-1.33f, -.93f, -1.42f, -.45f, stroke)
        canvas.drawLine(1.33f, -.93f, 1.42f, -.45f, stroke)
        fill.color = gold
        canvas.drawCircle(-1.41f, -.46f, .097f, fill)
        canvas.drawCircle(1.41f, -.46f, .097f, fill)
        stroke.strokeWidth = .031f
        canvas.drawCircle(-1.41f, -.46f, .097f, stroke)
        canvas.drawCircle(1.41f, -.46f, .097f, stroke)
    }

    private fun outlined(canvas: Canvas, path: Path, color: Int, outline: Int, width: Float) {
        fill.color = color
        canvas.drawPath(path, fill)
        drawStroke(canvas, path, outline, width)
    }

    private fun drawStroke(canvas: Canvas, path: Path, color: Int, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        canvas.drawPath(path, stroke)
    }
}
