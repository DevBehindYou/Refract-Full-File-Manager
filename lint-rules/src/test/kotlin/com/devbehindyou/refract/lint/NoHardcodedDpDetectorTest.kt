package com.devbehindyou.refract.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class NoHardcodedDpDetectorTest {

    @Test
    fun `flags a hardcoded dp literal outside the design system`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.browse.ui

                    val screenPadding = 16.dp
                    """.trimIndent()
                )
            )
            .issues(NoHardcodedDpDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `allows a dp literal inside core designsystem`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.core.designsystem

                    val spacingMedium = 16.dp
                    """.trimIndent()
                )
            )
            .issues(NoHardcodedDpDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `allows dp applied to a non-literal receiver`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.browse.ui

                    fun toPadding(base: Int) = base.dp
                    """.trimIndent()
                )
            )
            .issues(NoHardcodedDpDetector.ISSUE)
            .run()
            .expectClean()
    }
}
