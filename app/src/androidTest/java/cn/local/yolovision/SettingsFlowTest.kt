package cn.local.yolovision

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {
    @Test fun settingPersistsAfterActivityRecreation() {
        DeviceTestSupport.preservingPreferences { preferences ->
            // Override only the test's known starting state; preserve every original key in finally.
            assertTrue(preferences.edit().putString("task", VisionTask.DETECT.name)
                .putString("size", "n").putBoolean("useGpu", false)
                .putBoolean("showConfidence", false).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                awaitModelLoad(scenario)
                onView(withContentDescription("识别设置")).perform(click())
                onView(withText("显示置信度")).perform(scrollTo(), click())
                pressBack()
                scenario.recreate()
                awaitModelLoad(scenario)
                onView(withContentDescription("识别设置")).perform(click())
                onView(withText("显示置信度")).perform(scrollTo()).check(matches(isChecked()))
                DeviceTestSupport.evidence("settings", "confidence toggle persisted after activity recreation")
                pressBack()
            }
        }
    }

    private fun awaitModelLoad(scenario: ActivityScenario<MainActivity>) {
        DeviceTestSupport.await(scenario, "Model initialization") {
            DeviceTestSupport.field(it, "busy") == false
        }
    }
}
