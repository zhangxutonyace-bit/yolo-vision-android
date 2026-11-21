package cn.local.yolovision

import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Must run on an unlocked device. Merely compiling this class is not a privacy-flow pass. */
@RunWith(AndroidJUnit4::class)
class PrivacyFlowTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test fun existingCameraGrantCannotBypassConsentAcrossRecreationOrTaskLoading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DeviceTestSupport.preservingPreferences(acceptPrivacy = false) { settings ->
            assertTrue(context.getSharedPreferences(PrivacyPolicy.PREFERENCES_NAME, 0).edit().clear().commit())
            assertTrue(settings.edit().putString("size", "x").commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertGate(scenario)
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.recreate()
                assertGate(scenario)
                scenario.onActivity { activity ->
                    MainActivity::class.java.getDeclaredMethod("loadModel", VisionTask::class.java, String::class.java)
                        .apply { isAccessible = true }.invoke(activity, VisionTask.POSE, "n")
                }
                assertGate(scenario)
                onView(withText("不同意并退出")).perform(click())
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
                DeviceTestSupport.evidence("privacy-gate", "PASS granted camera remains unbound without consent; legacy x falls back to n")
            }
        }
    }

    @Test fun withdrawalStopsAnInitializedCameraAndRequiresConsentAgain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DeviceTestSupport.preservingPreferences { settings ->
            assertTrue(settings.edit().putString("task", VisionTask.DETECT.name)
                .putString("size", "n").putBoolean("useGpu", false).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                DeviceTestSupport.await(scenario, "Camera initialized for withdrawal check", 90_000) {
                    DeviceTestSupport.field(it, "latestResult") != null && engineField(it, "camera") != null
                }
                var beforeWithdrawal: Any? = null
                scenario.onActivity { beforeWithdrawal = DeviceTestSupport.field(it, "latestResult") }
                val pausedFile = File(context.cacheDir, "paused-frame.jpg").apply { writeBytes(byteArrayOf(1)) }
                val shareDirectory = File(context.cacheDir, "share").apply { mkdirs() }
                File(shareDirectory, "privacy-test.jpg").writeBytes(byteArrayOf(1))
                val modelsDirectory = File(context.filesDir, "models")
                File(modelsDirectory, "old-model.tflite").writeBytes(byteArrayOf(1))
                onView(withContentDescription("识别设置")).perform(click())
                onView(withText("隐私政策")).perform(scrollTo(), click())
                onView(withText("撤回同意")).perform(click())
                onView(withText("撤回同意")).perform(click())
                assertGate(scenario)
                assertNull(context.getSharedPreferences(PrivacyPolicy.PREFERENCES_NAME, 0)
                    .getString(PrivacyPolicy.CONSENT_KEY, null))
                scenario.onActivity { assertNull(DeviceTestSupport.field(it, "latestResult")) }
                DeviceTestSupport.await(scenario, "Privacy file cleanup", 20_000) {
                    !pausedFile.exists() && shareDirectory.listFiles().orEmpty().isEmpty() &&
                        modelsDirectory.listFiles().orEmpty().isEmpty()
                }
                onView(withText("同意并继续")).perform(click())
                DeviceTestSupport.await(scenario, "Fresh inference after agreeing again", 90_000) {
                    val result = DeviceTestSupport.field(it, "latestResult")
                    result != null && result !== beforeWithdrawal && engineField(it, "camera") != null
                }
                DeviceTestSupport.evidence("privacy-withdrawal", "PASS real camera stopped, private files deleted, fresh inference after agreement")
            }
        }
    }

    @Test fun clearAndRecreateWaitForTheSameFileQueueAndResumeInference() {
        DeviceTestSupport.preservingPreferences { settings ->
            assertTrue(settings.edit().putString("size", "n").putBoolean("useGpu", false).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                DeviceTestSupport.await(scenario, "Initial inference before clearing", 90_000) {
                    DeviceTestSupport.field(it, "latestResult") != null
                }
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                ApplicationFileWorker.executor.execute {
                    entered.countDown()
                    release.await(30, TimeUnit.SECONDS)
                }
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    onView(withContentDescription("识别设置")).perform(click())
                    onView(withText("清理本地数据")).perform(scrollTo(), click())
                    onView(withText("清理")).perform(click())
                    scenario.recreate()
                } finally { release.countDown() }
                DeviceTestSupport.await(scenario, "Inference after clear and Activity recreation", 90_000) {
                    DeviceTestSupport.field(it, "latestResult") != null && engineField(it, "camera") != null
                }
                DeviceTestSupport.evidence("privacy-clear-recreate", "PASS real inference after serialized cleanup and recreation")
            }
        }
    }

    private fun assertGate(scenario: ActivityScenario<MainActivity>) {
        onView(withText("同意并继续")).check(matches(isDisplayed()))
        scenario.onActivity {
            assertEquals(false, DeviceTestSupport.field(it, "ready"))
            assertEquals(false, DeviceTestSupport.field(it, "busy"))
            assertEquals(false, DeviceTestSupport.field(it, "lifecycleAttached"))
            assertEquals("n", DeviceTestSupport.field(it, "currentSize"))
            assertNull(engineField(it, "camera"))
            assertNull(engineField(it, "inferenceSnapshot"))
            assertNull(engineField(it, "inferenceResult"))
        }
    }

    private fun engineField(activity: MainActivity, name: String): Any? {
        val engine = requireNotNull(DeviceTestSupport.field(activity, "yolo"))
        return engine.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(engine)
    }
}
