package com.devbehindyou.refract.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * `runBlocking` outside a `test`/`androidTest` source set. It's a correct-in-tests,
 * dangerous-in-production tool — calling it from app code blocks the calling thread
 * (main thread, if called from UI code) until the coroutine completes
 * (CODING_RULES.md; MODULES.md's `NoRunBlocking` entry).
 */
class NoRunBlockingDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("runBlocking")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (context.isTestSource) return

        val declaringPackage = method.containingClass?.qualifiedName ?: return
        if (!declaringPackage.startsWith("kotlinx.coroutines")) return

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message =
                "`runBlocking` is only allowed in `test`/`androidTest` source " +
                    "sets. In app code, launch a coroutine on an injected `CoroutineScope` " +
                    "instead.",
        )
    }

    companion object {
        val ISSUE: Issue =
            Issue.create(
                id = "NoRunBlocking",
                briefDescription = "runBlocking used outside a test source set",
                explanation =
                    """
                    `runBlocking` blocks the calling thread until the coroutine completes. \
                    That's fine in a test's own thread, but calling it from production code \
                    (especially the main thread) defeats the point of using coroutines at all.
                    """.trimIndent(),
                category = Category.CORRECTNESS,
                priority = 9,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        NoRunBlockingDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
