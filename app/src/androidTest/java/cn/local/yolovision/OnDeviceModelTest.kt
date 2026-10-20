package cn.local.yolovision

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ultralytics.yolo.YOLO
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android CPU inference. This suite must be run on a connected device; compilation is not a pass. */
@RunWith(AndroidJUnit4::class)
class OnDeviceModelTest {
    @Test fun sevenBundledModelsLoadAndInferOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = instrumentation.context.assets.open("bus.jpg").use { BitmapFactory.decodeStream(it) }
        assertNotNull(bitmap)
        val store = ModelStore(context)
        val failures = mutableListOf<String>()
        try {
          for (task in VisionTask.entries) {
            DeviceTestSupport.evidence("model-${task.name}", "START bundled nano / actual Android CPU / official bus fixture")
            var model: YOLO? = null
            try {
                store.beginRequest()
                val path = store.resolve(task, "n") { _, _ -> }
                model = YOLO(context, path, task.engineTask, useGpu = false)
                val result = model.predict(bitmap)
                assertTrue("${task.name}: finite inference time", result.speed.isFinite() && result.speed >= 0)
                assertTrue("${task.name}: dimensions", result.origShape.width > 0 && result.origShape.height > 0)
                if (task != VisionTask.DEPTH) assertTrue("${task.name}: labels loaded", result.names.isNotEmpty())
                assertTrue("${task.name}: box confidences finite", result.boxes.all { it.conf.isFinite() })
                when (task) {
                    VisionTask.DETECT -> assertTrue("Official bus fixture should contain a bus/person", result.boxes.any { it.cls == "bus" || it.cls == "person" })
                    VisionTask.SEGMENT -> {
                        val masks = requireNotNull(result.masks)
                        assertTrue("Instance masks produced", masks.masks.isNotEmpty())
                        assertTrue("Each instance mask has pixels", masks.masks.all { it.isNotEmpty() && it.first().isNotEmpty() })
                        assertNotNull("Rendered instance mask", masks.combinedMask)
                    }
                    VisionTask.SEMANTIC -> {
                        val mask = requireNotNull(result.semanticMask)
                        assertTrue(mask.width > 0 && mask.height > 0)
                        assertEquals(mask.width * mask.height, mask.classMap.size)
                        assertTrue(mask.classMap.all { it >= 0 && it < result.names.size })
                    }
                    VisionTask.DEPTH -> {
                        val depth = requireNotNull(result.depthMap)
                        assertTrue(depth.width > 0 && depth.height > 0)
                        assertTrue(depth.minDepth.isFinite() && depth.maxDepth.isFinite())
                        val values = requireNotNull(depth.values)
                        assertEquals(depth.width * depth.height, values.size)
                        assertTrue(values.all { it.isFinite() })
                    }
                    VisionTask.CLASSIFY -> {
                        val probs = requireNotNull(result.probs)
                        assertTrue(probs.top1Label.isNotBlank() && probs.top1Conf.isFinite())
                        assertEquals(5, probs.top5Labels.size)
                        assertTrue(probs.top5Confs.all { it.isFinite() })
                    }
                    VisionTask.POSE -> {
                        assertTrue(result.keypointsList.isNotEmpty())
                        assertTrue(result.keypointsList.all { keypoints ->
                            keypoints.xy.size == 17 && keypoints.xy.all { it.first.isFinite() && it.second.isFinite() }
                        })
                    }
                    VisionTask.OBB -> {
                        // A street fixture may legitimately have no aerial/DOTA detections.
                        assertEquals(15, result.names.size)
                        assertTrue(result.obb.all { it.confidence.isFinite() })
                    }
                }
                DeviceTestSupport.evidence("model-${task.name}",
                    "PASS shape=${result.origShape.width}x${result.origShape.height}, speedMs=${result.speed}, " +
                        "accelerator=${result.accelerator}, boxes=${result.boxes.size}, masks=${result.masks?.masks?.size}, " +
                        "semantic=${result.semanticMask?.let { "${it.width}x${it.height}" }}, " +
                        "depth=${result.depthMap?.let { "${it.width}x${it.height}" }}, " +
                        "top1=${result.probs?.top1Label}, poses=${result.keypointsList.size}, obb=${result.obb.size}")
                result.annotatedImage?.takeIf { it !== bitmap }?.recycle()
            } catch (failure: Throwable) {
                if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
                val detail = "${task.name}: ${failure.javaClass.simpleName}: ${failure.message}"
                failures += detail
                DeviceTestSupport.evidence("model-${task.name}", "FAIL $detail")
            } finally {
                model?.close()
            }
          }
        } finally {
            bitmap.recycle()
        }
        assertTrue("Android CPU model failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
