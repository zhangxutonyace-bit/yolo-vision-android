package cn.local.yolovision

import android.util.AtomicFile
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileNotFoundException
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FunEffectSettingsTest {
    @Test fun savedTargetNameShapeAndBoxVisibilitySurviveDismissAndRecreation() {
        DeviceTestSupport.preservingPreferences { preferences ->
            assertTrue(preferences.edit().putString("task", VisionTask.DETECT.name)
                .putString("size", "n").putBoolean("useGpu", false).commit())

            preservingEffectFile {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                assertTrue(FunEffectStore(context).save(FunEffectConfig()))
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    openPanels()
                    control("识别目标").perform(click())
                    target("狗").perform(click()).check(matches(isSelected()))

                    control("显示特效").perform(click())
                    nameField().perform(replaceText("真机测试"), closeSoftKeyboard())
                    control("矩形").perform(click()).check(matches(isSelected()))
                    control("显示识别框").check(matches(isChecked())).perform(click()).check(matches(isNotChecked()))
                    control("矩形").check(matches(not(isDisplayed())))

                    // Switching panels must keep the other panel's draft intact.
                    control("识别目标").perform(click())
                    target("狗").check(matches(isSelected()))
                    control("保存并返回相机").perform(click())
                    onView(withContentDescription("识别设置")).check(matches(isDisplayed()))

                    // Dismissing a new draft must not overwrite that saved rule.
                    openPanels()
                    control("识别目标").perform(click())
                    target("狗").check(matches(isSelected()))
                    target("猫").perform(click())
                    pressBack()

                    scenario.recreate()
                    openPanels()
                    control("识别目标").perform(click())
                    target("狗").check(matches(isSelected()))
                    control("显示特效").perform(click())
                    nameField().check(matches(withText("真机测试")))
                    control("显示识别框").check(matches(isNotChecked())).perform(click()).check(matches(isChecked()))
                    control("矩形").check(matches(isSelected()))
                    pressBack()
                }
            }
        }
    }

    private fun openPanels() {
        onView(withContentDescription("识别设置")).perform(click())
        control("趣味特效").perform(scrollTo(), click())
    }

    private fun control(label: String) = onView(withText(label)).inRoot(isDialog())

    private fun target(label: String) = onView(withContentDescription(label)).inRoot(isDialog())

    private fun nameField() = onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog())

    /** Restore the original settings and logo before the preferences helper relaunches the app. */
    private fun preservingEffectFile(block: () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = AtomicFile(File(context.filesDir, "fun-effect.json"))
        val originalBytes = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            null
        }

        try {
            block()
        } finally {
            if (originalBytes == null) {
                file.delete()
            } else {
                val stream = file.startWrite()
                try {
                    stream.write(originalBytes)
                    file.finishWrite(stream)
                } catch (failure: Exception) {
                    file.failWrite(stream)
                    throw failure
                }
            }
        }
    }
}
