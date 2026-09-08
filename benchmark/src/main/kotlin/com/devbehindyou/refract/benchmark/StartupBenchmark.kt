package com.devbehindyou.refract.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 1's deferred AC4 (cold-start baseline — see `PHASE_1_NOTES.md` §4). Measures
 * [com.devbehindyou.refract.MainActivity]'s cold start.
 *
 * Cannot be run in the sandbox this project was built in (`PHASE_1_NOTES.md` §0) — it
 * needs a real device or emulator. This is the module existing and being structurally
 * correct, not a verified, measured baseline. `:app`'s applicationId has no
 * flavour/build-type suffix (see `app/build.gradle.kts`), so the target package name is
 * the same across every variant.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.devbehindyou.refract"
    }
}
