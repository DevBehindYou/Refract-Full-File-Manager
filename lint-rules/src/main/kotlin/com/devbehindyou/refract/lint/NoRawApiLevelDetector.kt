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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastBinaryOperator

/**
 * A numeric literal compared against `Build.VERSION.SDK_INT` must instead compare
 * against a named constant, so the comparison is self-documenting and greppable
 * (CODING_RULES.md — "no raw API levels"; MODULES.md's `NoRawApiLevel` entry).
 *
 * Scope note: this flags the *literal* side of the comparison specifically. A
 * comparison against `Build.VERSION_CODES.Q` is a qualified reference, not a literal,
 * so it already passes this check without this detector needing an opinion on whether
 * that specific named constant is the "right" one to use.
 */
class NoRawApiLevelDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UBinaryExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator !in COMPARISON_OPERATORS) return

                val sdkIntSide: UElement
                val literalSide: UElement
                when {
                    isSdkIntReference(node.leftOperand) -> {
                        sdkIntSide = node.leftOperand
                        literalSide = node.rightOperand
                    }
                    isSdkIntReference(node.rightOperand) -> {
                        sdkIntSide = node.rightOperand
                        literalSide = node.leftOperand
                    }
                    else -> return
                }
                @Suppress("UNUSED_EXPRESSION")
                sdkIntSide // referenced for clarity only

                val literal = literalSide as? ULiteralExpression ?: return
                if (literal.value !is Int) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message =
                        "Comparing `Build.VERSION.SDK_INT` against the raw literal " +
                            "`${literal.value}`. Use a named constant (e.g. an `Api` object " +
                            "member, or `Build.VERSION_CODES.*`) instead.",
                )
            }
        }

    private fun isSdkIntReference(element: UElement): Boolean {
        val text = element.asSourceString().filterNot { it.isWhitespace() }
        return text == "Build.VERSION.SDK_INT" || text.endsWith(".Build.VERSION.SDK_INT") ||
            text == "SDK_INT" || text.endsWith(".SDK_INT")
    }

    companion object {
        private val COMPARISON_OPERATORS =
            setOf(
                UastBinaryOperator.GREATER,
                UastBinaryOperator.GREATER_OR_EQUALS,
                UastBinaryOperator.LESS,
                UastBinaryOperator.LESS_OR_EQUALS,
                UastBinaryOperator.EQUALS,
                UastBinaryOperator.NOT_EQUALS,
                UastBinaryOperator.IDENTITY_EQUALS,
                UastBinaryOperator.IDENTITY_NOT_EQUALS,
            )

        val ISSUE: Issue =
            Issue.create(
                id = "NoRawApiLevel",
                briefDescription = "Raw integer literal compared against SDK_INT",
                explanation =
                    """
                    Comparing `Build.VERSION.SDK_INT` against a bare integer literal (e.g. \
                    `Build.VERSION.SDK_INT >= 29`) makes the comparison hard to grep and easy \
                    to get wrong. Compare against a named constant instead.
                    """.trimIndent(),
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        NoRawApiLevelDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
