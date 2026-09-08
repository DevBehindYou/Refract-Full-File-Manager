package com.devbehindyou.refract.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class NoRawApiLevelDetectorTest {

    @Test
    fun `flags a raw literal compared against SDK_INT`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.data.local

                    import android.os.Build

                    fun supportsScopedStorage(): Boolean = Build.VERSION.SDK_INT >= 29
                    """.trimIndent()
                )
            )
            .issues(NoRawApiLevelDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `flags the literal regardless of which side of the comparison it is on`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.data.local

                    import android.os.Build

                    fun isLegacy(): Boolean = 29 > Build.VERSION.SDK_INT
                    """.trimIndent()
                )
            )
            .issues(NoRawApiLevelDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `allows a comparison against a named VERSION_CODES constant`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.data.local

                    import android.os.Build

                    fun supportsScopedStorage(): Boolean =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    """.trimIndent()
                )
            )
            .issues(NoRawApiLevelDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `allows an unrelated integer comparison`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.data.local

                    fun isOverLimit(count: Int): Boolean = count >= 29
                    """.trimIndent()
                )
            )
            .issues(NoRawApiLevelDetector.ISSUE)
            .run()
            .expectClean()
    }
}
