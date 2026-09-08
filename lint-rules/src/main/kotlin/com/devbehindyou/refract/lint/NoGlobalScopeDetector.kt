package com.devbehindyou.refract.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler

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
            override fun visitSimpleNameReferenceExpression(
                node: USimpleNameReferenceExpression
            ) {
                if (node.identifier != "GlobalScope") return

                val resolvedPackage = node.resolve()
                    ?.let { context.evaluator.getPackage(it)?.qualifiedName }
                // If resolution succeeds, require it to actually be kotlinx.coroutines'
                // GlobalScope (not an unrelated identifier that happens to be named the
                // same). If resolution fails (can happen in some Lint invocation modes),
                // fall back to flagging on name alone rather than silently passing.
                if (resolvedPackage != null && resolvedPackage != "kotlinx.coroutines") return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = "`GlobalScope` must not be used. It outlives the component " +
                        "that launched it and can't be cancelled — inject a scoped " +
                        "`CoroutineScope` instead."
                )
            }
        }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "NoGlobalScope",
            briefDescription = "GlobalScope used",
            explanation = """
                `GlobalScope` launches a coroutine with no owner and no cancellation path — \
                it outlives whatever component started it. Inject a scoped `CoroutineScope` \
                instead, everywhere, including in tests.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                NoGlobalScopeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
