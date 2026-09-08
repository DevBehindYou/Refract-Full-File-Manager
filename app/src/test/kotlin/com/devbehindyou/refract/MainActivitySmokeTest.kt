package com.devbehindyou.refract

import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Definition-of-done, per AI_DEVELOPMENT_GUIDE.md: "Runs on an API 27 emulator and an
 * API 36 emulator without a crash." This is the Robolectric approximation of that
 * check, run on every PR; the real emulator matrix runs at merge-to-main
 * (testing/TEST_STRATEGY.md §11).
 *
 * Uses JUnit4 + RobolectricTestRunner rather than JUnit5, deliberately, for this one
 * file — see PHASE_1_NOTES.md for the reasoning (it's run through the same JUnit
 * Platform test task as everything else, via the vintage engine).
 *
 * Parameterised across both ends of minSdk (27) and targetSdk (36) via
 * @Config(sdk = ...) rather than two separate test classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 36])
class MainActivitySmokeTest {

    @Test
    fun mainActivityLaunchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assert(!activity.isFinishing) {
                    "MainActivity finished immediately instead of launching cleanly"
                }
            }
        }
    }
}
