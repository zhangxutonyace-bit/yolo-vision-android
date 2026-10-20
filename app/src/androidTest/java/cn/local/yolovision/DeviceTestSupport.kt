package cn.local.yolovision

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/** Test-only helpers. Never leave the person's task, model or display preferences changed. */
internal object DeviceTestSupport {
    fun <T> preservingPreferences(block: (SharedPreferences) -> T): T {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("vision", 0)
        val original = preferences.all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
        try {
            return block(preferences)
        } finally {
            val editor = preferences.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
                    null -> Unit
                    else -> error("Unsupported preference type: ${value.javaClass.name}")
                }
            }
            check(editor.commit()) { "Could not restore original vision preferences" }
            check(preferences.all == original) { "Restored preferences differ from their original values" }
            evidence("preferences", "restored ${original.size} original keys")
            // The scenario inside block has been closed before this finally runs.
            // Recreate the visible app from the restored preferences, even after a failed assertion.
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            ))
            instrumentation.waitForIdleSync()
        }
    }

    fun field(activity: MainActivity, name: String): Any? =
        MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    fun await(
        scenario: ActivityScenario<MainActivity>,
        description: String,
        timeoutMs: Long = 60_000,
        condition: (MainActivity) -> Boolean
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var satisfied = false
        while (!satisfied && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { satisfied = condition(it) }
            if (!satisfied) SystemClock.sleep(150)
        }
        assertTrue("$description did not complete within ${timeoutMs / 1000} seconds", satisfied)
    }

    fun evidence(stage: String, detail: String) {
        val message = "YOLO_DEVICE_EVIDENCE $stage: $detail"
        Log.i("YOLODeviceEvidence", message)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("stream", "\n$message\n")
        })
    }
}
