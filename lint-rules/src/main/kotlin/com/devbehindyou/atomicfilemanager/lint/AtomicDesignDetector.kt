package com.devbehindyou.atomicfilemanager.lint

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.getContainingUFile

/**
 * Atomic design-system rules (docs/roadmap/ATOMIC_UI_PLAN.md §14.2): outside
 * `core.designsystem`, screens must not hand-pick colours, use filled icons, show toasts or
 * alert dialogs, or animate with springs that overshoot. All issues start as warnings while
 * screens are migrated and become errors at the cleanup step (U8).
 */
class AtomicDesignDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("Color", "makeText", "AlertDialog", "spring")

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UQualifiedReferenceExpression::class.java)

    private fun isExempt(
        context: JavaContext,
        element: UElement,
    ): Boolean {
        if (context.isTestSource) return true
        val packageName = element.getContainingUFile()?.packageName ?: return true
        return packageName.startsWith(AtomicPackages.CORE_DESIGNSYSTEM)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (isExempt(context, node)) return
        val owner = method.containingClass?.qualifiedName.orEmpty()
        when (method.name) {
            "Color" ->
                if (owner.startsWith(COMPOSE_GRAPHICS) && node.valueArguments.singleOrNull() is ULiteralExpression) {
                    report(context, node, NO_HARDCODED_COLOR, "Hand-picked colour. Use a role from `Atomic.colors`.")
                }
            "makeText" ->
                if (owner == "android.widget.Toast") {
                    report(
                        context,
                        node,
                        NO_TOAST,
                        "Toast. Use the Atomic snackbar so feedback stays in the app's language.",
                    )
                }
            "AlertDialog" ->
                if (owner.startsWith("androidx.compose.material3")) {
                    report(
                        context,
                        node,
                        NO_ALERT_DIALOG,
                        "AlertDialog. Use an Atomic sheet (ATOMIC_UI_PLAN.md §7.10).",
                    )
                }
            "spring" ->
                if (owner.startsWith("androidx.compose.animation.core") && overshoots(context, node, method)) {
                    report(context, node, NO_BOUNCY_SPRING, "Spring that overshoots. Atomic motion never bounces.")
                }
        }
    }

    private fun overshoots(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ): Boolean =
        context.evaluator.computeArgumentMapping(node, method).filterValues { it.name == "dampingRatio" }.keys.any {
                argument ->
            val source = argument.asSourceString()
            BOUNCY_RATIOS.any { source.endsWith(it) } ||
                ((argument as? ULiteralExpression)?.value as? Number)?.toFloat()?.let { it < 1f } == true
        }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val selector = node.selector.asSourceString()
                val receiver = node.receiver.asSourceString()
                val filled = selector == "Filled" || selector == "Default"
                val iconsRoot = receiver == "Icons" || receiver.endsWith("Icons.AutoMirrored")
                if (filled && iconsRoot && !isExempt(context, node)) {
                    report(context, node, NO_FILLED_ICONS, "Filled icon. Use `AtomicIcons` (outlined only).")
                }
            }
        }

    private fun report(
        context: JavaContext,
        node: UElement,
        issue: Issue,
        message: String,
    ) = context.report(issue = issue, scope = node, location = context.getLocation(node), message = message)

    companion object {
        private const val COMPOSE_GRAPHICS = "androidx.compose.ui.graphics"
        private val BOUNCY_RATIOS =
            listOf("DampingRatioLowBouncy", "DampingRatioMediumBouncy", "DampingRatioHighBouncy")

        private val IMPLEMENTATION = Implementation(AtomicDesignDetector::class.java, Scope.JAVA_FILE_SCOPE)

        private fun issue(
            id: String,
            brief: String,
            explanation: String,
        ): Issue =
            Issue.create(
                id = id,
                briefDescription = brief,
                explanation = explanation,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                implementation = IMPLEMENTATION,
            )

        val NO_HARDCODED_COLOR =
            issue(
                "NoHardcodedColor",
                "Colour literal outside the design system",
                "Colours come from the Atomic roles so light, dark and wallpaper accents stay correct.",
            )
        val NO_FILLED_ICONS =
            issue(
                "NoFilledIcons",
                "Filled or default icon outside the design system",
                "The Atomic family uses outlined icons only. Reference `AtomicIcons` instead.",
            )
        val NO_TOAST =
            issue(
                "NoToast",
                "Toast used for feedback",
                "Feedback uses the Atomic snackbar, which can carry an Undo action and is announced to TalkBack.",
            )
        val NO_ALERT_DIALOG =
            issue(
                "NoAlertDialog",
                "Material AlertDialog outside the design system",
                "Atomic flows use bottom sheets on phones and centred panels on large screens.",
            )
        val NO_BOUNCY_SPRING =
            issue(
                "NoBouncySpring",
                "Spring animation that overshoots",
                "Atomic motion uses `ease` timings; gesture motion uses critically damped springs only.",
            )

        val ISSUES = listOf(NO_HARDCODED_COLOR, NO_FILLED_ICONS, NO_TOAST, NO_ALERT_DIALOG, NO_BOUNCY_SPRING)
    }
}
