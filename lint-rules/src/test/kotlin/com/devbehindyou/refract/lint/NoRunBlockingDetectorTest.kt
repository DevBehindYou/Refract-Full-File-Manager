package com.devbehindyou.refract.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class NoRunBlockingDetectorTest {
    /**
     * The real `kotlinx-coroutines-core` jar isn't on this module's test classpath, so a
     * minimal stub is supplied for `runBlocking` to resolve against — the standard way
     * to unit-test a Lint check that needs a resolved call target, without pulling in
     * the real dependency (see Google's own custom-lint-rules sample for the same
     * pattern applied to Android SDK stubs).
     */
    private val coroutinesStub =
        kotlin(
            """
            package kotlinx.coroutines

            fun <T> runBlocking(block: () -> T): T = TODO()
            """.trimIndent(),
        )

    @Test
    fun `flags kotlinx coroutines runBlocking called from production code`() {
        lint()
            .files(
                coroutinesStub,
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.browse.viewmodel

                    import kotlinx.coroutines.runBlocking

                    fun loadSync() {
                        runBlocking { }
                    }
                    """.trimIndent(),
                ),
            )
            .issues(NoRunBlockingDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `allows a same-named function from an unrelated package`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.browse.viewmodel

                    fun runBlocking(block: () -> Unit) = block()

                    fun loadSync() {
                        runBlocking { }
                    }
                    """.trimIndent(),
                ),
            )
            .issues(NoRunBlockingDetector.ISSUE)
            .run()
            .expectClean()
    }

    // The test/androidTest source-set exemption itself (`context.isTestSource`) is a
    // well-established Lint API flag driven by the real Gradle source set at build
    // time; it isn't re-exercised here because TestLintTask's synthetic-project source
    // set placement isn't something this suite asserts on with confidence — see
    // PHASE_1_NOTES.md.
}
