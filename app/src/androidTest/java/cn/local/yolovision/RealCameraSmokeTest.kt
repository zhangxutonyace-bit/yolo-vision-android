package cn.local.yolovision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.View
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.ultralytics.yolo.YOLOResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses the device's real CameraX frames and bundled CPU model.
 * A locked device, occupied camera or missing inference is a failure, never a skip.
 * Object presence depends on the real scene; recognizing a named physical object is a separate visual check.
 */
@RunWith(AndroidJUnit4::class)
class RealCameraSmokeTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun realCameraInfersPausesAndResumesWithNewFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Real camera permission must be granted", PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
        DeviceTestSupport.preservingPreferences { preferences ->
            assertTrue(preferences.edit().putString("task", VisionTask.DETECT.name)
                .putString("size", "n").putBoolean("useGpu", false).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                DeviceTestSupport.await(scenario, "Actual camera inference (camera occupied/locked is a failure)", 90_000) {
                    DeviceTestSupport.field(it, "ready") == true &&
                        DeviceTestSupport.field(it, "latestResult") is YOLOResult
                }
                var beforePause: YOLOResult? = null
                scenario.onActivity {
                    beforePause = DeviceTestSupport.field(it, "latestResult") as YOLOResult
                    val result = beforePause!!
                    assertTrue(result.origShape.width > 0 && result.origShape.height > 0)
                    assertTrue(result.speed.isFinite() && result.speed >= 0)
                    DeviceTestSupport.evidence("real-camera",
                        "PASS shape=${result.origShape.width}x${result.origShape.height}, " +
                            "boxes=${result.boxes.size}, labels=${result.boxes.map { box -> box.cls }}, " +
                            "speedMs=${result.speed}, accelerator=${result.accelerator}")
                }
                onView(withContentDescription("暂停识别")).perform(click())
                DeviceTestSupport.await(scenario, "Pausing the actual inference frame", 10_000) {
                    DeviceTestSupport.field(it, "paused") == true &&
                        DeviceTestSupport.field(it, "frozenJpeg") is ByteArray
                }
                var pausedResult: YOLOResult? = null
                scenario.onActivity {
                    pausedResult = DeviceTestSupport.field(it, "latestResult") as YOLOResult
                    val jpeg = DeviceTestSupport.field(it, "frozenJpeg") as ByteArray
                    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    assertNotNull("Paused real camera JPEG must decode", bitmap)
                    assertTrue(bitmap.width > 0 && bitmap.height > 0)
                    assertEquals(View.VISIBLE, (DeviceTestSupport.field(it, "frozenImage") as View).visibility)
                    DeviceTestSupport.evidence("pause", "PASS real JPEG ${bitmap.width}x${bitmap.height}, bytes=${jpeg.size}")
                    bitmap.recycle()
                }
                SystemClock.sleep(1_000)
                scenario.onActivity {
                    assertEquals(true, DeviceTestSupport.field(it, "paused"))
                    assertSame("Paused result must remain stable", pausedResult, DeviceTestSupport.field(it, "latestResult"))
                }
                onView(withContentDescription("继续识别")).perform(click())
                DeviceTestSupport.await(scenario, "A new real camera inference after resume", 60_000) {
                    val result = DeviceTestSupport.field(it, "latestResult") as? YOLOResult
                    DeviceTestSupport.field(it, "paused") == false &&
                        result != null && result !== pausedResult && result !== beforePause
                }
                scenario.onActivity {
                    assertNull(DeviceTestSupport.field(it, "frozenJpeg"))
                    assertEquals(View.GONE, (DeviceTestSupport.field(it, "frozenImage") as View).visibility)
                    DeviceTestSupport.evidence("resume", "PASS new real camera inference received")
                }
            }
        }
    }
}
