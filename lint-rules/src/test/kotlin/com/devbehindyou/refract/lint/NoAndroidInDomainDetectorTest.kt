package com.devbehindyou.refract.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class NoAndroidInDomainDetectorTest {

    @Test
    fun `flags an android_content_Context import in the domain package`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.domain.model

                    import android.content.Context

                    class FileNode(private val context: Context)
                    """.trimIndent()
                )
            )
            .issues(NoAndroidInDomainDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `flags a java_io_File import in the domain package`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.domain.model

                    import java.io.File

                    class FileNode(private val backing: File)
                    """.trimIndent()
                )
            )
            .issues(NoAndroidInDomainDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `allows pure Kotlin domain code`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.domain.model

                    data class FileNode(val id: String, val name: String, val sizeBytes: Long)
                    """.trimIndent()
                )
            )
            .issues(NoAndroidInDomainDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `allows an android import outside the domain package`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.devbehindyou.refract.data.local

                    import android.content.Context

                    class LocalFileSource(private val context: Context)
                    """.trimIndent()
                )
            )
            .issues(NoAndroidInDomainDetector.ISSUE)
            .run()
            .expectClean()
    }
}
