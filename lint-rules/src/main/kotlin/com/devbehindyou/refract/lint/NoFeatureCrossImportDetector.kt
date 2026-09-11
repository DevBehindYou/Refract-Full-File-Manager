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
 * `feature.a` must not import from `feature.b`. Features only ever depend on `core.*`
 * and `domain` — never on each other — so any one feature can be deleted without
 * touching the others (architecture/MODULES.md, module-dependencies.md).
 */
class NoFeatureCrossImportDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val ownFeature = featureNameOf(node.getContainingUFile()?.packageName) ?: return
                val imported = node.importedFqName() ?: return
                val importedFeature = featureNameOf(imported) ?: return

                if (importedFeature == ownFeature) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message =
                        "`feature.$ownFeature` must not import from " +
                            "`feature.$importedFeature`. Features only depend on `core.*` and " +
                            "`domain` — share code through those, not directly between features.",
                )
            }
        }

    companion object {
        /**
         * `com.devbehindyou.refract.feature.trash.ui.TrashScreen` -> `"trash"`.
         * Returns null for anything not under `feature.<name>`.
         */
        private fun featureNameOf(fqName: String?): String? {
            if (fqName == null) return null
            val prefix = "${RefractPackages.FEATURE}."
            if (!fqName.startsWith(prefix)) return null
            val rest = fqName.removePrefix(prefix)
            return rest.substringBefore('.').takeIf { it.isNotEmpty() }
        }

        val ISSUE: Issue =
            Issue.create(
                id = "NoFeatureCrossImport",
                briefDescription = "One feature package imports another feature package",
                explanation =
                    """
                    `feature.a` must not import `feature.b`. Every feature depends only on \
                    `core.*` and `domain`, never on a sibling feature, so any one feature can \
                    be deleted without touching the others.
                    """.trimIndent(),
                category = Category.CORRECTNESS,
                priority = 8,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        NoFeatureCrossImportDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
