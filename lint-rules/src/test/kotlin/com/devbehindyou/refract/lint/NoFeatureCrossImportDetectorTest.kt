package com.devbehindyou.refract.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class NoFeatureCrossImportDetectorTest {

    @Test
    fun `flags feature trash importing from feature browse`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.trash.ui

                    import com.devbehindyou.refract.feature.browse.ui.BrowseScreenState

                    class TrashScreenState(val lastBrowsed: BrowseScreenState)
                    """.trimIndent()
                )
            )
            .issues(NoFeatureCrossImportDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `allows a feature importing from core`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.trash.ui

                    import com.devbehindyou.refract.core.designsystem.RefractSpacing

                    class TrashScreenState(val spacing: RefractSpacing)
                    """.trimIndent()
                )
            )
            .issues(NoFeatureCrossImportDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `allows a feature importing from its own subpackages`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.trash.ui

                    import com.devbehindyou.refract.feature.trash.viewmodel.TrashViewModel

                    class TrashScreenState(val viewModel: TrashViewModel)
                    """.trimIndent()
                )
            )
            .issues(NoFeatureCrossImportDetector.ISSUE)
            .run()
            .expectClean()
    }
}
