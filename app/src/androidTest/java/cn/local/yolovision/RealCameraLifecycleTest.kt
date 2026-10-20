package cn.local.yolovision

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.ultralytics.yolo.YOLOResult
import com.ultralytics.yolo.YOLOView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Short real-camera lifecycle check. No synthetic frames and no production API changes. */
@RunWith(AndroidJUnit4::class)
class RealCameraLifecycleTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun frontBackCameraPausedRecreationAndBackgroundResume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cameras = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val facings = cameras.cameraIdList.map {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)
        }
        val hasBoth = CameraCharacteristics.LENS_FACING_FRONT in facings &&
            CameraCharacteristics.LENS_FACING_BACK in facings
        DeviceTestSupport.evidence("camera-capabilities", "front-and-back=$hasBoth; advertisedFacings=$facings")
        assertTrue("Front/back switching cannot be verified: this device does not expose both camera directions", hasBoth)

        DeviceTestSupport.preservingPreferences { preferences ->
            assertTrue(preferences.edit().putString("task", VisionTask.DETECT.name)
                .putString("size", "n").putBoolean("useGpu", false).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitFreshResult(scenario, null, "Initial real camera")
                assertSelectedFacing(scenario, CameraCharacteristics.LENS_FACING_BACK, false)
                var previous: YOLOResult? = result(scenario)

                // Exercise the visible controls. Rebinding alone is insufficient: require a new inference result.
                onView(withContentDescription("切换镜头")).perform(click())
                awaitFreshResult(scenario, previous, "Front camera after visible switch control")
                assertSelectedFacing(scenario, CameraCharacteristics.LENS_FACING_FRONT, true)
                DeviceTestSupport.evidence("front-camera", "PASS selected front lens and received a new real inference")

                previous = result(scenario)
                onView(withContentDescription("切换镜头")).perform(click())
                awaitFreshResult(scenario, previous, "Back camera after visible switch control")
                assertSelectedFacing(scenario, CameraCharacteristics.LENS_FACING_BACK, false)
                DeviceTestSupport.evidence("back-camera", "PASS selected back lens and received a new real inference")

                onView(withContentDescription("暂停识别")).perform(click())
                DeviceTestSupport.await(scenario, "Pause before activity recreation", 10_000) {
                    DeviceTestSupport.field(it, "paused") == true &&
                        DeviceTestSupport.field(it, "frozenJpeg") is ByteArray
                }
                var pausedBytes = byteArrayOf()
                var previousActivity: MainActivity? = null
                scenario.onActivity {
                    previousActivity = it
                    pausedBytes = (DeviceTestSupport.field(it, "frozenJpeg") as ByteArray).clone()
                    assertTrue(pausedBytes.isNotEmpty())
                }

                scenario.recreate()
                DeviceTestSupport.await(scenario, "Restore paused frame after activity recreation") {
                    DeviceTestSupport.field(it, "ready") == true &&
                        DeviceTestSupport.field(it, "busy") == false &&
                        DeviceTestSupport.field(it, "paused") == true
                }
                scenario.onActivity {
                    assertNotSame("Activity must actually be recreated", previousActivity, it)
                    assertPausedSnapshot(it, pausedBytes)
                }
                // Verify that lifecycle onStart/onResume does not silently resume a deliberately paused camera.
                SystemClock.sleep(1_000)
                scenario.onActivity { assertPausedSnapshot(it, pausedBytes) }
                DeviceTestSupport.evidence("paused-recreation",
                    "PASS new activity retained byte-identical JPEG (${pausedBytes.size} bytes), remained paused")

                onView(withContentDescription("继续识别")).perform(click())
                awaitFreshResult(scenario, null, "Real camera after explicitly resuming recreated activity")
                scenario.onActivity {
                    assertEquals(false, DeviceTestSupport.field(it, "paused"))
                    assertNull(DeviceTestSupport.field(it, "frozenJpeg"))
                }
                DeviceTestSupport.evidence("recreated-resume", "PASS explicit resume received a new real inference")

                scenario.moveToState(Lifecycle.State.CREATED)
                SystemClock.sleep(1_000)
                val beforeForeground = result(scenario)
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitFreshResult(scenario, beforeForeground, "A new real inference after background/foreground")
                DeviceTestSupport.evidence("background-foreground",
                    "PASS CREATED to RESUMED lifecycle transition delivered a new real inference")
            }
        }
    }

    private fun result(scenario: ActivityScenario<MainActivity>): YOLOResult? {
        var value: YOLOResult? = null
        scenario.onActivity { value = DeviceTestSupport.field(it, "latestResult") as? YOLOResult }
        return value
    }

    private fun awaitFreshResult(
        scenario: ActivityScenario<MainActivity>,
        previous: YOLOResult?,
        description: String
    ) = DeviceTestSupport.await(scenario, description, 60_000) {
        val latest = DeviceTestSupport.field(it, "latestResult") as? YOLOResult
        DeviceTestSupport.field(it, "ready") == true &&
            DeviceTestSupport.field(it, "paused") == false &&
            latest != null && latest !== previous
    }

    private fun assertSelectedFacing(
        scenario: ActivityScenario<MainActivity>,
        expected: Int,
        front: Boolean
    ) {
        scenario.onActivity {
            assertEquals(front, DeviceTestSupport.field(it, "frontCamera"))
            val view = DeviceTestSupport.field(it, "yolo") as YOLOView
            val selectedFacing = YOLOView::class.java.getDeclaredField("lensFacing")
                .apply { isAccessible = true }.getInt(view)
            assertEquals("Engine must select the requested camera direction", expected, selectedFacing)
            assertNotNull("A real CameraX camera must be bound",
                YOLOView::class.java.getDeclaredField("camera").apply { isAccessible = true }.get(view))
        }
    }

    private fun assertPausedSnapshot(activity: MainActivity, expected: ByteArray) {
        assertEquals(true, DeviceTestSupport.field(activity, "paused"))
        assertArrayEquals("Restored pause JPEG must preserve all original bytes",
            expected, DeviceTestSupport.field(activity, "frozenJpeg") as ByteArray)
        assertEquals(View.VISIBLE, (DeviceTestSupport.field(activity, "frozenImage") as View).visibility)
        assertNull("Recreated paused activity must not receive new live inference",
            DeviceTestSupport.field(activity, "latestResult"))
    }
}
