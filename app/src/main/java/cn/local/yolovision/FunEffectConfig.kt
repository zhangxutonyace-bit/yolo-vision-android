package cn.local.yolovision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.AtomicFile
import android.util.Base64
import androidx.core.os.BundleCompat
import com.ultralytics.yolo.FunEffectOverlayStyle
import com.ultralytics.yolo.HeadEffect
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class FunEffectTarget(val title: String, val classIndex: Int) {
    PERSON("人", 0), CAR("汽车", 2), CAT("猫", 15), DOG("狗", 16);

    companion object {
        fun restore(value: String?) = entries.firstOrNull { it.name == value } ?: PERSON
    }
}

internal data class FunEffectConfig(
    val target: FunEffectTarget = FunEffectTarget.PERSON,
    val displayName: String = "小满厨房",
    val rounded: Boolean = true,
    val logo: Bitmap? = null,
    val headEffect: HeadEffect = HeadEffect.NONE,
    val showBox: Boolean = true
) {
    val activeHeadEffect: HeadEffect
        get() = if (target == FunEffectTarget.PERSON) headEffect else HeadEffect.NONE

    fun overlay(defaultLogo: Bitmap) = FunEffectOverlayStyle(
        target.classIndex, displayName, rounded, logo ?: defaultLogo,
        headEffect = activeHeadEffect, showBox = showBox
    )

    fun toBundle() = Bundle().apply {
        putString("target", target.name)
        putString("name", displayName)
        putBoolean("rounded", rounded)
        putParcelable("logo", logo)
        putString("headEffect", headEffect.name)
        putBoolean("showBox", showBox)
    }

    companion object {
        const val MAX_NAME_LENGTH = 32

        fun fromBundle(bundle: Bundle) = FunEffectConfig(
            FunEffectTarget.restore(bundle.getString("target")),
            bundle.getString("name").orEmpty().take(MAX_NAME_LENGTH),
            bundle.getBoolean("rounded", true),
            BundleCompat.getParcelable(bundle, "logo", Bitmap::class.java),
            headEffect = HeadEffect.restore(
                bundle.getString("headEffect").orEmpty().takeIf { bundle.containsKey("headEffect") },
                bundle.getBoolean("catEars", false)
            ),
            showBox = bundle.getBoolean("showBox", true)
        )
    }
}

/** Store the logo and settings together so a failed write cannot mix two configurations. */
internal class FunEffectStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "fun-effect.json"))

    @Synchronized
    fun read(): FunEffectConfig = runCatching {
        val data = JSONObject(file.readFully().toString(Charsets.UTF_8))
        val logo = data.optString("logo").takeIf { it.isNotEmpty() }?.let {
            val png = Base64.decode(it, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(png, 0, png.size)
        }
        FunEffectConfig(
            FunEffectTarget.restore(data.optString("target")),
            data.optString("name").trim().take(FunEffectConfig.MAX_NAME_LENGTH)
                .ifBlank { FunEffectConfig().displayName },
            data.optBoolean("rounded", true),
            logo,
            headEffect = HeadEffect.restore(
                data.optString("headEffect").takeIf { data.has("headEffect") },
                data.optBoolean("catEars", false)
            ),
            showBox = data.optBoolean("showBox", true)
        )
    }.getOrDefault(FunEffectConfig())

    @Synchronized
    fun save(config: FunEffectConfig): Boolean = runCatching {
        val data = JSONObject().apply {
            put("target", config.target.name)
            put("name", config.displayName)
            put("rounded", config.rounded)
            put("headEffect", config.headEffect.name)
            put("showBox", config.showBox)
            config.logo?.let { bitmap ->
                val png = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, png))
                put("logo", Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP))
            }
        }
        val output = file.startWrite()
        try {
            output.write(data.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
    }.isSuccess

    @Synchronized
    fun clear(): Boolean {
        // AtomicFile.delete also removes any interrupted write and backup.
        file.delete()
        return !file.baseFile.exists()
    }
}

internal object FunEffectLogo {
    private const val MAX_SIDE = 192

    fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val (width, height) = fit(info.size.width, info.size.height)
                decoder.setTargetSize(width, height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val (width, height) = fit(bounds.outWidth, bounds.outHeight)
            val largest = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (largest / sample >= MAX_SIDE * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.let {
                Bitmap.createScaledBitmap(it, width, height, true)
            }
        }
    }.getOrNull()

    private fun fit(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = (MAX_SIDE.toDouble() / max(width, height)).coerceAtMost(1.0)
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    fun spatula(): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 4f
        }
        canvas.rotate(42f, 32f, 32f)
        canvas.drawLine(32f, 30f, 32f, 54f, pen)
        pen.style = Paint.Style.STROKE
        canvas.drawRoundRect(22f, 9f, 42f, 31f, 4f, 4f, pen)
        pen.strokeWidth = 2f
        for (x in listOf(28f, 32f, 36f)) canvas.drawLine(x, 15f, x, 25f, pen)
    }
}
