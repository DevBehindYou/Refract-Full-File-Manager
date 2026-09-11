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
import org.jetbrains.uast.USimpleNameReferenceExpression

/**
 * `GlobalScope` used anywhere. Unlike [NoRunBlockingDetector], this has no test-source
 * exemption — MODULES.md's `NoGlobalScope` entry is unqualified ("GlobalScope
 * anywhere"), and a leaked, unstructured coroutine is exactly as unaccountable in a
 * test as it is in production.
 */
class NoGlobalScopeDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(USimpleNameReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (node.identifier != "GlobalScope") return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message =
                        "`GlobalScope` must not be used. It outlives the component " +
                            "that launched it and can't be cancelled — inject a scoped " +
                            "`CoroutineScope` instead.",
                )
            }
        }

    companion object {
        val ISSUE: Issue =
            Issue.create(
                id = "NoGlobalScope",
                briefDescription = "GlobalScope used",
                explanation =
                    """
                    `GlobalScope` launches a coroutine with no owner and no cancellation path — \
                    it outlives whatever component started it. Inject a scoped `CoroutineScope` \
                    instead, everywhere, including in tests.
                    """.trimIndent(),
                category = Category.CORRECTNESS,
                priority = 9,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        NoGlobalScopeDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
