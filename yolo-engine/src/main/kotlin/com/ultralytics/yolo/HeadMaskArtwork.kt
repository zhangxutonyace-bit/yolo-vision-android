package com.ultralytics.yolo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** Original stickers in an eye-aligned coordinate space with one unit between the eyes. */
internal class HeadMaskArtwork {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val bunnyEar = Path().apply {
        moveTo(-.65f, -1.23f)
        cubicTo(-.97f, -1.34f, -1.22f, -2.02f, -1.14f, -2.42f)
        cubicTo(-1.10f, -2.66f, -.88f, -2.71f, -.74f, -2.49f)
        cubicTo(-.52f, -2.15f, -.46f, -1.59f, -.52f, -1.33f)
        quadTo(-.55f, -1.22f, -.65f, -1.23f)
        close()
    }
    private val bunnyEarInner = Path().apply {
        moveTo(-.70f, -1.45f)
        cubicTo(-.89f, -1.54f, -1.07f, -2.12f, -1.00f, -2.34f)
        quadTo(-.96f, -2.47f, -.86f, -2.34f)
        cubicTo(-.72f, -2.11f, -.60f, -1.70f, -.64f, -1.49f)
        quadTo(-.65f, -1.43f, -.70f, -1.45f)
        close()
    }
    private val cloudHair = Path().apply {
        moveTo(-1.25f, -.79f)
        cubicTo(-1.60f, -.87f, -1.64f, -1.33f, -1.33f, -1.49f)
        cubicTo(-1.39f, -1.79f, -1.02f, -1.98f, -.78f, -1.80f)
        cubicTo(-.72f, -2.11f, -.25f, -2.19f, -.09f, -1.90f)
        cubicTo(.09f, -2.16f, .48f, -2.13f, .62f, -1.89f)
        cubicTo(.89f, -2.05f, 1.20f, -1.91f, 1.23f, -1.65f)
        cubicTo(1.56f, -1.61f, 1.64f, -1.20f, 1.40f, -.99f)
        cubicTo(1.43f, -.72f, 1.10f, -.55f, .90f, -.70f)
        cubicTo(.69f, -.50f, .36f, -.58f, .21f, -.76f)
        cubicTo(.03f, -.51f, -.31f, -.51f, -.45f, -.70f)
        cubicTo(-.67f, -.48f, -1.04f, -.52f, -1.25f, -.79f)
        close()
    }

    private val fruitShell = Path().apply {
        moveTo(-1.36f, -.75f)
        quadTo(-1.27f, -.96f, -1.02f, -.90f)
        quadTo(0f, -.61f, 1.02f, -.90f)
        quadTo(1.27f, -.96f, 1.36f, -.75f)
        cubicTo(1.47f, .36f, .92f, 1.63f, 0f, 1.70f)
        cubicTo(-.92f, 1.63f, -1.47f, .36f, -1.36f, -.75f)
        close()
        addFaceOpenings(this)
    }
    private val fruitFlesh = Path().apply {
        moveTo(-1.14f, -.63f)
        quadTo(0f, -.33f, 1.14f, -.63f)
        cubicTo(1.22f, .37f, .72f, 1.43f, 0f, 1.49f)
        cubicTo(-.72f, 1.43f, -1.22f, .37f, -1.14f, -.63f)
        close()
        addFaceOpenings(this)
    }

    private val comicFrame = Path().apply {
        moveTo(0f, -.83f)
        cubicTo(-.31f, -1.08f, -.91f, -1.03f, -1.17f, -.71f)
        cubicTo(-1.53f, -.21f, -1.38f, .58f, -1.03f, 1.05f)
        cubicTo(-.75f, 1.44f, -.42f, 1.61f, 0f, 1.65f)
        cubicTo(.42f, 1.61f, .75f, 1.44f, 1.03f, 1.05f)
        cubicTo(1.38f, .58f, 1.53f, -.21f, 1.17f, -.71f)
        cubicTo(.91f, -1.03f, .31f, -1.08f, 0f, -.83f)
        close()
        addFaceOpenings(this)
    }
    private val comicBrow = Path().apply {
        moveTo(-1.04f, -.39f)
        quadTo(-.83f, -.75f, -.42f, -.63f)
        quadTo(-.17f, -.56f, -.12f, -.42f)
        quadTo(-.48f, -.50f, -.89f, -.37f)
        quadTo(-1.04f, -.30f, -1.04f, -.39f)
        close()
    }
    private val comicSpark = Path().apply {
        moveTo(-.96f, .34f)
        lineTo(-.73f, .34f)
        lineTo(-.83f, .54f)
        lineTo(-.65f, .55f)
        lineTo(-1.03f, .91f)
        lineTo(-.94f, .66f)
        lineTo(-1.11f, .65f)
        close()
    }

    fun drawRabbit(canvas: Canvas) {
        val mirror = canvas.save()
        drawBunnyEar(canvas)
        canvas.scale(-1f, 1f)
        drawBunnyEar(canvas)
        canvas.restoreToCount(mirror)

        canvas.drawPath(cloudHair, fill(Color.rgb(204, 184, 255)))
        canvas.drawPath(cloudHair, stroke(Color.rgb(83, 58, 137), .065f))
        val curls = stroke(Color.rgb(248, 220, 255), .065f)
        canvas.drawArc(-1.33f, -1.51f, -.76f, -.98f, 169f, 146f, false, curls)
        canvas.drawArc(-.70f, -1.78f, -.18f, -1.20f, 172f, 129f, false, curls)
        canvas.drawArc(.07f, -1.77f, .61f, -1.18f, 170f, 135f, false, curls)
        canvas.drawArc(.74f, -1.61f, 1.27f, -1.08f, 175f, 130f, false, curls)
        canvas.drawCircle(-1.16f, -.94f, .09f, fill(Color.rgb(255, 174, 214)))
        canvas.drawCircle(1.15f, -.88f, .10f, fill(Color.rgb(255, 174, 214)))
    }

    fun drawFruitMask(canvas: Canvas) {
        canvas.drawPath(fruitShell, fill(Color.rgb(147, 218, 94)))
        canvas.drawPath(fruitFlesh, fill(Color.rgb(255, 103, 119)))
        canvas.drawPath(fruitShell, stroke(Color.rgb(32, 107, 65), .064f))
        val seed = fill(Color.rgb(82, 41, 70))
        canvas.drawOval(-1.04f, .34f, -.94f, .54f, seed)
        canvas.drawOval(.94f, .34f, 1.04f, .54f, seed)
        canvas.drawOval(-.70f, .73f, -.61f, .90f, seed)
        canvas.drawOval(.61f, .73f, .70f, .90f, seed)
        canvas.drawOval(-.07f, .33f, .07f, .52f, seed)
        canvas.drawOval(-.045f, 1.15f, .045f, 1.29f, seed)
    }

    fun drawComicMask(canvas: Canvas) {
        canvas.drawPath(comicFrame, fill(Color.rgb(130, 226, 245)))
        canvas.drawPath(comicFrame, stroke(Color.rgb(38, 64, 129), .073f))
        canvas.drawOval(-1.12f, .28f, -.67f, .64f, fill(Color.rgb(255, 155, 210)))
        canvas.drawOval(.67f, .28f, 1.12f, .64f, fill(Color.rgb(255, 155, 210)))
        val mirror = canvas.save()
        drawComicDetails(canvas)
        canvas.scale(-1f, 1f)
        drawComicDetails(canvas)
        canvas.restoreToCount(mirror)
    }

    private fun drawBunnyEar(canvas: Canvas) {
        canvas.drawPath(bunnyEar, fill(Color.rgb(239, 215, 255)))
        canvas.drawPath(bunnyEarInner, fill(Color.rgb(252, 156, 205)))
        canvas.drawPath(bunnyEar, stroke(Color.rgb(83, 58, 137), .065f))
    }

    private fun drawComicDetails(canvas: Canvas) {
        canvas.drawPath(comicBrow, fill(Color.rgb(41, 67, 144)))
        canvas.drawPath(comicSpark, fill(Color.rgb(255, 228, 109)))
        canvas.drawPath(comicSpark, stroke(Color.rgb(41, 67, 144), .026f))
        canvas.drawCircle(-1.15f, -.12f, .065f, fill(Color.WHITE))
    }

    private fun addFaceOpenings(path: Path) {
        path.fillType = Path.FillType.EVEN_ODD
        path.addOval(-.85f, -.24f, -.17f, .24f, Path.Direction.CW)
        path.addOval(.17f, -.24f, .85f, .24f, Path.Direction.CW)
        path.addOval(-.43f, .57f, .43f, 1.08f, Path.Direction.CW)
    }

    private fun fill(colorValue: Int) = fill.apply { color = colorValue }

    private fun stroke(colorValue: Int, width: Float) = stroke.apply {
        color = colorValue
        strokeWidth = width
    }
}
