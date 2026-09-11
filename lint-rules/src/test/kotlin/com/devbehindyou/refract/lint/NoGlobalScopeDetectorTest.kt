package com.devbehindyou.refract.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class NoGlobalScopeDetectorTest {
    @Test
    fun `flags a GlobalScope launch`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.browse.viewmodel

                    object GlobalScope {
                        fun launch(block: () -> Unit) = block()
                    }

                    fun fireAndForget() {
                        GlobalScope.launch { }
                    }
                    """.trimIndent(),
                ),
            )
            .issues(NoGlobalScopeDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `allows an identifier that is not literally named GlobalScope`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.browse.viewmodel

                    class ScreenScope {
                        fun launch(block: () -> Unit) = block()
                    }

                    fun fireAndForget(scope: ScreenScope) {
                        scope.launch { }
                    }
                    """.trimIndent(),
                ),
            )
            .issues(NoGlobalScopeDetector.ISSUE)
            .run()
            .expectClean()
    }
}
