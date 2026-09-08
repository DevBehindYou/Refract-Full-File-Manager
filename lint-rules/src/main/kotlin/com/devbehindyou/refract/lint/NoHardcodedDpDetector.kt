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
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.getContainingUFile

/**
 * A `.dp` literal (e.g. `16.dp`) outside `core.designsystem` — spacing values must come
 * from the design-system's token set, not be hand-typed at each call site
 * (CODING_RULES.md; MODULES.md's `NoHardcodedDp` entry).
 *
 * Scope note: this detector matches the `.dp` extension specifically, as that's the
 * exact rule named in the spec. Equivalent checks for `.sp`, durations, or colour
 * literals are deliberately out of scope here and would be separate detectors.
 */
class NoHardcodedDpDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val packageName = node.getContainingUFile()?.packageName ?: return
                if (packageName.startsWith(RefractPackages.CORE_DESIGNSYSTEM)) return

                if (node.selector.asSourceString() != "dp") return
                if (node.receiver !is ULiteralExpression) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = "Hardcoded `${node.asSourceString()}`. Use a spacing token " +
                        "from `core.designsystem` instead of a literal `.dp` value."
                )
            }
        }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "NoHardcodedDp",
            briefDescription = "Hardcoded .dp literal outside the design system",
            explanation = """
                A `.dp` literal (e.g. `16.dp`) was found outside `core.designsystem`. \
                Spacing must come from the design system's token set so it can be changed \
                in one place.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoHardcodedDpDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
