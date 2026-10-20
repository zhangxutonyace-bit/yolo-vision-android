package cn.local.yolovision

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray

/** Model downloads never include camera data; all inference remains on device. */
class ModelStore(private val context: Context) {
    private val directory = File(context.filesDir, "models").apply { mkdirs() }
    private val cancelled = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null
    private val manifest by lazy {
        val json = context.assets.open("models/download-manifest.json").bufferedReader().use { it.readText() }.removePrefix("\uFEFF")
        val array = JSONArray(json)
        (0 until array.length()).associate { index ->
            val entry = array.getJSONObject(index)
            entry.getString("file") to (entry.getString("sha256") to entry.getLong("bytes"))
        }
    }

    fun available(task: VisionTask, size: String): Boolean = size == "n" || valid(File(directory, task.fileName(size)))
    fun expectedSize(task: VisionTask, size: String): Long = manifest[task.fileName(size)]?.second ?: 0L
    fun beginRequest() { cancelled.set(false) }

    fun resolve(task: VisionTask, size: String, progress: (Long, Long) -> Unit): String {
        if (cancelled.get()) throw CancellationException()
        val target = File(directory, task.fileName(size))
        val expected = manifest[task.fileName(size)] ?: throw IOException("模型不在已验证的下载清单中")
        if (verified(target, expected.first, expected.second)) return target.absolutePath
        val temporary = File(directory, "${task.fileName(size)}.part")
        try {
            if (size == "n") {
                context.assets.open("models/${task.fileName(size)}").use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                val request = URL(task.modelUrl(size)).openConnection() as HttpURLConnection
                connection = request
                request.connectTimeout = 20_000
                request.readTimeout = 30_000
                request.instanceFollowRedirects = true
                if (request.responseCode !in 200..299) throw IOException("服务器返回 ${request.responseCode}")
                val total = request.contentLengthLong
                if (total > MAX_BYTES) throw IOException("模型文件超过下载大小限制")
                request.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        while (true) {
                            if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > MAX_BYTES) throw IOException("模型文件超过下载大小限制")
                            output.write(buffer, 0, count)
                            progress(received, total)
                        }
                        if (total > 0 && received != total) throw IOException("模型下载不完整，请重试")
                    }
                }
            }
            if (cancelled.get()) throw CancellationException()
            if (!verified(temporary, expected.first, expected.second)) throw IOException("模型完整性校验失败，请重新下载")
            if (target.exists() && !target.delete()) throw IOException("无法替换损坏的模型文件")
            if (!temporary.renameTo(target)) throw IOException("无法保存模型文件")
            return target.absolutePath
        } catch (error: IOException) {
            if (cancelled.get()) throw CancellationException("已取消模型下载")
            throw error
        } finally {
            connection?.disconnect()
            connection = null
            temporary.delete()
        }
    }

    fun cancel() {
        cancelled.set(true)
        connection?.disconnect()
    }

    companion object {
        const val MAX_BYTES = 512L * 1024 * 1024
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
