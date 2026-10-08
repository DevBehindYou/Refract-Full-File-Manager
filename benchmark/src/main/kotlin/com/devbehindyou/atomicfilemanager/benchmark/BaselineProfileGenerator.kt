package com.devbehindyou.atomicfilemanager.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile (ALL_IN_ONE_PLAN.md §16.1, H4): cold start, then the Files
 * tab and a scroll, the paths a person hits first. Run on an emulator or rooted/API 33+ device:
 *
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest -P android.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.BaselineProfileGenerator`
 *
 * then copy the `*-baseline-prof.txt` from `benchmark/build/outputs/connected_android_test_additional_output/`
 * to `app/src/main/baseline-prof.txt`. `profileinstaller` in :app installs it on first launch.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndBrowse() =
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            device.findObject(By.text("Files"))?.click()
            device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
            device.findObject(By.scrollable(true))?.let { list ->
                list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }

    private companion object {
        const val TARGET_PACKAGE = "com.devbehindyou.atomicfilemanager"
        const val WAIT_MS = 5_000L
        const val GESTURE_MARGIN_DIVISOR = 5
    }
}
