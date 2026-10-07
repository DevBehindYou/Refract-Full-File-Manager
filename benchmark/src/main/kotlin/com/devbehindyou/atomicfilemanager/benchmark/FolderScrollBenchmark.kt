package com.devbehindyou.atomicfilemanager.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scroll smoothness in Files (ALL_IN_ONE_PLAN.md §7.5, §16.1): opens the Files tab and flings the
 * list. Needs a device or emulator with storage access granted to the app and a folder with
 * enough items to scroll (for example DCIM/Camera); with nothing to scroll it measures nothing.
 */
@RunWith(AndroidJUnit4::class)
class FolderScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFiles() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(),
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.findObject(By.text("Files"))?.click()
                device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
            },
        ) {
            val list = device.findObject(By.scrollable(true)) ?: return@measureRepeated
            list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            repeat(FLINGS) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }

    private companion object {
        const val TARGET_PACKAGE = "com.devbehindyou.atomicfilemanager"
        const val WAIT_MS = 5_000L
        const val FLINGS = 3
        const val GESTURE_MARGIN_DIVISOR = 5
    }
}
