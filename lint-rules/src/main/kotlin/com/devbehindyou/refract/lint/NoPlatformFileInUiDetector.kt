package com.devbehindyou.refract.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.getContainingUFile

/**
 * UI code (`feature.*`, `core.ui`) must not talk to platform storage types directly —
 * `java.io.File`, `android.net.Uri`, `android.content.ContentResolver`, or
 * `androidx.documentfile.provider.DocumentFile`. UI renders domain models; only `data`
 * is allowed to know these types exist.
 */
class NoPlatformFileInUiDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val packageName = node.getContainingUFile()?.packageName ?: return
                val inUiLayer =
                    packageName.startsWith(RefractPackages.FEATURE) ||
                        packageName.startsWith(RefractPackages.CORE_UI)
                if (!inUiLayer) return

                val imported = node.importedFqName() ?: return
                if (imported !in FORBIDDEN_TYPES) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message =
                        "`$imported` must not be imported in UI code. Expose a " +
                            "domain model from `data` instead of a raw platform storage type.",
                )
            }
        }

    companion object {
        private val FORBIDDEN_TYPES =
            setOf(
                "java.io.File",
                "android.net.Uri",
                "android.content.ContentResolver",
                "androidx.documentfile.provider.DocumentFile",
            )

        val ISSUE: Issue =
            Issue.create(
                id = "NoPlatformFileInUi",
                briefDescription = "Platform storage type imported in UI code",
                explanation =
                    """
                    `feature.*` and `core.ui` packages render domain models — they must not \
                    import `java.io.File`, `android.net.Uri`, `android.content.ContentResolver`, \
                    or `androidx.documentfile.provider.DocumentFile` directly. Route this through \
                    a domain model exposed by `data`.
                    """.trimIndent(),
                category = Category.CORRECTNESS,
                priority = 8,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        NoPlatformFileInUiDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
