package cn.local.yolovision

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

class VisionContractTest {
    @Test fun allTasksResolveToDistinctPinnedOfficialAssets() {
        val names = VisionTask.entries.flatMap { task -> VisionTask.MODEL_SIZES.map { size -> task.fileName(size) } }
        assertEquals(35, names.distinct().size)
        assertEquals("yolo26n_w8a32.tflite", VisionTask.DETECT.fileName("n"))
        assertEquals("yolo26n-depth_w8a32.tflite", VisionTask.DEPTH.fileName("n"))
        assertTrue(VisionTask.POSE.modelUrl("s").startsWith(VisionTask.MODEL_BASE + "/"))
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
