package com.devbehindyou.atomicfilemanager.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.jupiter.api.Test

class NoFeatureCrossImportDetectorTest {
    @Test
    fun `flags feature trash importing from feature browse`() {
        lint()
            .testModes(TestMode.DEFAULT)
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.atomicfilemanager.feature.trash.ui

                    import com.devbehindyou.atomicfilemanager.feature.browse.ui.BrowseScreenState

                    class TrashScreenState(val lastBrowsed: BrowseScreenState)
                    """.trimIndent(),
                ),
            )
            .issues(NoFeatureCrossImportDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `allows a feature importing from core`() {
        lint()
            .testModes(TestMode.DEFAULT)
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.atomicfilemanager.feature.trash.ui

                    import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicSpacing

                    class TrashScreenState(val spacing: AtomicSpacing)
                    """.trimIndent(),
                ),
            )
            .issues(NoFeatureCrossImportDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `allows a feature importing from its own subpackages`() {
        lint()
            .testModes(TestMode.DEFAULT)
            .allowCompilationErrors()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.atomicfilemanager.feature.trash.ui

                    import com.devbehindyou.atomicfilemanager.feature.trash.viewmodel.TrashViewModel

                    class TrashScreenState(val viewModel: TrashViewModel)
                    """.trimIndent(),
                ),
            )
            .issues(NoFeatureCrossImportDetector.ISSUE)
            .run()
            .expectClean()
    }
}
