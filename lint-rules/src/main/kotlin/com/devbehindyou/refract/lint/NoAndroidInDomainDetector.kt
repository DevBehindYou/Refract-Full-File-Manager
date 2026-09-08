package com.devbehindyou.refract.lint

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
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.getContainingUFile

/**
 * The `domain` package must be pure Kotlin — no `android.*`, `androidx.*`, or
 * `java.io.File` import may appear there. That boundary is what keeps domain logic
 * testable without an Android runtime and swappable across storage backends
 * (SAF vs. shell vs. root — see architecture/ARCHITECTURE.md and
 * architecture/DOMAIN_LAYER.md).
 */
class NoAndroidInDomainDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val packageName = node.getContainingUFile()?.packageName ?: return
                if (!packageName.startsWith(RefractPackages.DOMAIN)) return

                val imported = node.importedFqName() ?: return
                val isForbidden = imported == FORBIDDEN_FILE_CLASS ||
                    FORBIDDEN_PREFIXES.any { imported.startsWith(it) }
                if (!isForbidden) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = "`domain` must not import `$imported`. The domain layer is " +
                        "pure Kotlin — move any Android- or java.io.File-dependent code " +
                        "behind a domain-owned interface implemented in `data`."
                )
            }
        }

    companion object {
        private val FORBIDDEN_PREFIXES = listOf("android.", "androidx.")
        private const val FORBIDDEN_FILE_CLASS = "java.io.File"

        val ISSUE: Issue = Issue.create(
            id = "NoAndroidInDomain",
            briefDescription = "Android or java.io.File import in the domain layer",
            explanation = """
                The `domain` package must have zero `android.*`/`androidx.*` imports and \
                must not import `java.io.File`. This is what makes domain logic runnable \
                as plain JVM unit tests and independent of any single storage backend.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoAndroidInDomainDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
