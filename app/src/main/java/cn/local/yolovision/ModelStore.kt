package cn.local.yolovision

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray

/** Resolves verified bundled models without any network access. */
class ModelStore(private val context: Context) {
    private val directory = File(context.filesDir, "models").apply { mkdirs() }
    private val cancelled = AtomicBoolean(false)
    private val manifest by lazy {
        try {
            val json = context.assets.open("models/download-manifest.json").bufferedReader().use { it.readText() }.removePrefix("\uFEFF")
            val array = JSONArray(json)
            (0 until array.length()).associate { index ->
                val entry = array.getJSONObject(index)
                entry.getString("file") to (entry.getString("sha256") to entry.getLong("bytes"))
            }
        } catch (error: Exception) {
            throw IOException("内置模型清单无法读取，请重新安装应用", error)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun available(task: VisionTask, size: String): Boolean = size in VisionTask.MODEL_SIZES
    fun expectedSize(task: VisionTask, size: String): Long = manifest[task.fileName(size)]?.second ?: 0L
    fun beginRequest() { cancelled.set(false) }

    fun resolve(task: VisionTask, size: String, progress: (Long, Long) -> Unit): String {
        checkActive(cancelled::get)
        val target = File(directory, task.fileName(size))
        val expected = manifest[task.fileName(size)] ?: throw IOException("内置模型清单缺失，请重新安装应用")
        if (verified(target, expected.first, expected.second)) {
            checkActive(cancelled::get)
            return target.absolutePath
        }
        val temporary = File(directory, "${task.fileName(size)}.part")
        try {
            context.assets.open("models/${task.fileName(size)}").use { input ->
                temporary.outputStream().use { output ->
                    copyAsset(input, output, expected.second, cancelled::get, progress)
                }
            }
            checkActive(cancelled::get)
            if (!verified(temporary, expected.first, expected.second)) throw IOException("内置模型完整性校验失败，请重新安装应用")
            checkActive(cancelled::get)
            if (target.exists() && !target.delete()) throw IOException("无法替换损坏的模型文件")
            if (!temporary.renameTo(target)) throw IOException("无法保存模型文件")
            return target.absolutePath
        } catch (error: IOException) {
            checkActive(cancelled::get)
            throw IOException("无法准备内置模型，请检查可用空间；如仍失败，请重新安装应用", error)
        } finally {
            temporary.delete()
        }
    }

    fun cancel() {
        cancelled.set(true)
    }

    companion object {
        private fun checkActive(isCancelled: () -> Boolean) {
            if (isCancelled() || Thread.currentThread().isInterrupted) throw CancellationException("已取消模型准备")
        }

        internal fun copyAsset(input: InputStream, output: OutputStream, expectedBytes: Long,
                               isCancelled: () -> Boolean, progress: (Long, Long) -> Unit) {
            val buffer = ByteArray(64 * 1024)
            var copied = 0L
            while (true) {
                checkActive(isCancelled)
                val count = input.read(buffer)
                if (count < 0) break
                checkActive(isCancelled)
                copied += count
                if (copied > expectedBytes) throw IOException("内置模型大小异常，请重新安装应用")
                output.write(buffer, 0, count)
                progress(copied, expectedBytes)
            }
            checkActive(isCancelled)
            if (copied != expectedBytes) throw IOException("内置模型不完整，请重新安装应用")
        }

        fun verified(file: File, expectedHash: String, expectedBytes: Long): Boolean {
            if (!valid(file) || file.length() != expectedBytes) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) } == expectedHash
        }
        fun valid(file: File): Boolean {
            if (!file.isFile || file.length() < 1024) return false
            return try {
                val signature = ByteArray(8)
                file.inputStream().use { it.read(signature) } == 8 &&
                    String(signature, 4, 4, Charsets.US_ASCII) == "TFL3"
            } catch (_: IOException) { false }
        }
    }
}
