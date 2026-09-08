package com.devbehindyou.refract.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class NoPlatformFileInUiDetectorTest {

    @Test
    fun `flags an android_net_Uri import in a feature package`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.browse.ui

                    import android.net.Uri

                    class BrowseScreenState(val currentUri: Uri)
                    """.trimIndent()
                )
            )
            .issues(NoPlatformFileInUiDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `flags a java_io_File import in core_ui`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.core.ui.widgets

                    import java.io.File

                    class ThumbnailLoader(private val file: File)
                    """.trimIndent()
                )
            )
            .issues(NoPlatformFileInUiDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `allows a domain model import in a feature package`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.feature.browse.ui

                    import com.devbehindyou.refract.domain.model.FileNode

                    class BrowseScreenState(val current: FileNode)
                    """.trimIndent()
                )
            )
            .issues(NoPlatformFileInUiDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `allows a java_io_File import in the data layer`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.data.local

                    import java.io.File

                    class LocalFileRepository(private val root: File)
                    """.trimIndent()
                )
            )
            .issues(NoPlatformFileInUiDetector.ISSUE)
            .run()
            .expectClean()
    }
}
