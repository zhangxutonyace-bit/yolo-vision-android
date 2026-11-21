package cn.local.yolovision

import com.ultralytics.yolo.HeadEffect
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class VisionContractTest {
    private val pinnedBundledModels = mapOf(
        "yolo26n_w8a32.tflite" to ("d9cef07ce652ccfa9ce58e4ac8a4df98ff037739a9dad20a8afcae21b545df73" to 2875553L),
        "yolo26n-seg_w8a32.tflite" to ("3a9d800e59071e32516511bd788bf73a229e8b62c09eed96d6789894bc238acf" to 3234566L),
        "yolo26n-sem_w8a32.tflite" to ("d29dee0289f29be7e1446095e843109cdff7bdddba9bfd2ffd525425fbc5f557" to 1748412L),
        "yolo26n-depth_w8a32.tflite" to ("8c7f242c2a8c339c6721863e103e17e026cbd784c9a3d031a8e902660c364311" to 5474749L),
        "yolo26n-cls_w8a32.tflite" to ("0e39b06acd73645d71374a5ea8468ee9cd90a72f89df18fd11a4f97248a573ea" to 2985143L),
        "yolo26n-pose_w8a32.tflite" to ("50ff5869a7f77db2890eb4b23f00d678e9a77cdea899100712218c2c3a91dbc7" to 3512325L),
        "yolo26n-obb_w8a32.tflite" to ("259b8b41e290f9431eae816e86b33422a53eac490a8ea2715f70d6da798ba5d3" to 2926110L)
    )

    @Test fun allTasksResolveOnlyToTheSevenBundledNanoAssets() {
        val names = VisionTask.entries.flatMap { task -> VisionTask.MODEL_SIZES.map { size -> task.fileName(size) } }
        assertEquals(listOf("n"), VisionTask.MODEL_SIZES)
        assertEquals(pinnedBundledModels.keys, names.toSet())
        assertEquals(7, names.size)
        assertEquals("yolo26n_w8a32.tflite", VisionTask.DETECT.fileName("n"))
        assertEquals("yolo26n-depth_w8a32.tflite", VisionTask.DEPTH.fileName("n"))
    }
    @Test fun legacyModelChoicesFallBackToTheBundledSize() {
        listOf(null, "", "n", "s", "m", "l", "x", "../outside").forEach { stored ->
            assertEquals("n", VisionTask.restoreSize(stored))
        }
        VisionTask.entries.forEach { task ->
            listOf("s", "m", "l", "x").forEach { size ->
                assertThrows(IllegalArgumentException::class.java) { task.fileName(size) }
            }
        }
    }
    @Test fun headEffectsUsePoseLandmarksOnlyForEnabledPersonEffects() {
        assertEquals(VisionTask.DETECT, VisionTask.DETECT.modelForEffect(true, FunEffectConfig()))
        for (effect in HeadEffect.entries.filter { it != HeadEffect.NONE }) {
            val person = FunEffectConfig(headEffect = effect)
            assertEquals(VisionTask.POSE, VisionTask.DETECT.modelForEffect(true, person))
            assertEquals(VisionTask.DETECT, VisionTask.DETECT.modelForEffect(false, person))
            for (target in FunEffectTarget.entries.filter { it != FunEffectTarget.PERSON }) {
                assertEquals(VisionTask.DETECT, VisionTask.DETECT.modelForEffect(true, person.copy(target = target)))
            }
            for (task in VisionTask.entries.filter { it != VisionTask.DETECT }) {
                assertEquals(task, task.modelForEffect(true, person))
            }
        }
    }
    @Test fun headEffectSelectionRestoresOldCatEarsAndNewNamedEffects() {
        assertEquals(HeadEffect.CAT_EARS, HeadEffect.restore(null, legacyCatEars = true))
        assertEquals(HeadEffect.NONE, HeadEffect.restore(null, legacyCatEars = false))
        assertEquals(HeadEffect.NONE, HeadEffect.restore("unknown", legacyCatEars = true))
        for (effect in HeadEffect.entries) {
            assertEquals(effect, HeadEffect.restore(effect.name, legacyCatEars = true))
        }
        assertEquals(listOf(HeadEffect.KITCHEN_ORBIT), HeadEffect.entries.filter { it.animated })
    }
    @Test fun bundledModelsAndManifestKeepTheirPublishedIntegrity() {
        val directory = File("src/main/assets/models").takeIf { it.isDirectory }
            ?: File("app/src/main/assets/models")
        assertTrue("Bundled model directory must exist", directory.isDirectory)
        val manifest = File(directory, "download-manifest.json").readText()
        val entries = Regex("\\{[^{}]*}").findAll(manifest).map { it.value }.toList()
        assertEquals(7, entries.size)
        val names = entries.map { entry ->
            Regex("\"file\"\\s*:\\s*\"([^\"]+)\"").find(entry)!!.groupValues[1]
        }
        assertEquals(pinnedBundledModels.keys, names.toSet())
        pinnedBundledModels.forEach { (name, pinned) ->
            assertTrue("Unchanged verified model: $name", ModelStore.verified(File(directory, name), pinned.first, pinned.second))
            val entry = entries[names.indexOf(name)]
            assertEquals(pinned.first, Regex("\"sha256\"\\s*:\\s*\"([^\"]+)\"").find(entry)!!.groupValues[1])
            assertEquals(pinned.second, Regex("\"bytes\"\\s*:\\s*(\\d+)").find(entry)!!.groupValues[1].toLong())
        }
    }
    @Test fun cancellingLocalModelCopyStopsBeforeFurtherWrites() {
        val bytes = ByteArray(3 * 64 * 1024)
        val output = ByteArrayOutputStream()
        var cancelled = false
        assertThrows(CancellationException::class.java) {
            ModelStore.copyAsset(ByteArrayInputStream(bytes), output, bytes.size.toLong(), { cancelled }) { _, _ ->
                cancelled = true
            }
        }
        assertEquals(64 * 1024, output.size())
        val untouched = ByteArrayOutputStream()
        assertThrows(CancellationException::class.java) {
            ModelStore.copyAsset(ByteArrayInputStream(bytes), untouched, bytes.size.toLong(), { true }) { _, _ -> }
        }
        assertEquals(0, untouched.size())
    }
    @Test fun localModelCopyRejectsTruncatedAndOversizedAssets() {
        listOf(1023L, 1025L).forEach { expected ->
            assertThrows(IOException::class.java) {
                ModelStore.copyAsset(ByteArrayInputStream(ByteArray(1024)), ByteArrayOutputStream(), expected, { false }) { _, _ -> }
            }
        }
    }
    @Test(expected = IllegalArgumentException::class) fun arbitraryModelPathsAreRejected() {
        VisionTask.DETECT.fileName("../outside")
    }
    @Test fun irrelevantNmsControlsAreDisabledForDenseTasksAndClassification() {
        assertFalse(VisionTask.DEPTH.supportsIou)
        assertFalse(VisionTask.SEMANTIC.supportsIou)
        assertFalse(VisionTask.CLASSIFY.supportsIou)
        assertTrue(VisionTask.DETECT.supportsIou)
    }
    @Test fun invalidPersistedSettingsCannotProduceInvalidThresholds() {
        val sanitized = VisionSettings(Float.NaN, -2f).validated()
        assertEquals(.25f, sanitized.confidence)
        assertEquals(.05f, sanitized.iou)
        assertEquals(.95f, VisionSettings(confidence = 2f).validated().confidence)
        assertEquals(VisionTask.DETECT, VisionTask.restore("unavailable"))
    }
    @Test fun modelIntegrityRejectsTruncatedOrModifiedData() {
        val file = Files.createTempFile("model-integrity", ".tflite").toFile()
        try {
            val bytes = ByteArray(2048).apply { "TFL3".toByteArray().copyInto(this, 4) }
            file.writeBytes(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertTrue(ModelStore.verified(file, hash, 2048))
            assertFalse(ModelStore.verified(file, hash, 2047))
            bytes[100] = 1
            file.writeBytes(bytes)
            assertFalse(ModelStore.verified(file, hash, 2048))
        } finally { file.delete() }
    }
}
