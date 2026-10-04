package com.devbehindyou.atomicfilemanager.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.jupiter.api.Test

class AtomicDesignDetectorTest {
    // Minimal stubs so calls resolve without the real Compose and Android jars.
    private val stubs =
        arrayOf(
            kotlin(
                """
                package androidx.compose.ui.graphics

                @JvmInline
                value class Color(val value: ULong)

                fun Color(color: Long): Color = TODO()
                """.trimIndent(),
            ),
            kotlin(
                """
                package androidx.compose.animation.core

                object Spring {
                    const val DampingRatioMediumBouncy = 0.5f
                    const val DampingRatioNoBouncy = 1f
                }

                fun <T> spring(dampingRatio: Float = 1f, stiffness: Float = 1500f): Any = TODO()
                """.trimIndent(),
            ),
            kotlin(
                """
                package androidx.compose.material3

                fun AlertDialog(onDismissRequest: () -> Unit) {}
                """.trimIndent(),
            ),
            java(
                """
                package android.widget;

                public class Toast {
                    public static Toast makeText(Object context, CharSequence text, int duration) { return null; }
                }
                """.trimIndent(),
            ),
            kotlin(
                """
                package androidx.compose.material.icons

                object Icons {
                    object Filled
                    object Default
                    object Outlined
                }
                """.trimIndent(),
            ),
        )

    private fun check(source: String) =
        lint()
            .files(*stubs, kotlin(source))
            .issues(*AtomicDesignDetector.ISSUES.toTypedArray())
            .allowMissingSdk()
            .run()

    @Test
    fun `flags each design rule in a screen`() {
        check(
            """
            package com.devbehindyou.atomicfilemanager.ui.screens

            import android.widget.Toast
            import androidx.compose.animation.core.Spring
            import androidx.compose.animation.core.spring
            import androidx.compose.material.icons.Icons
            import androidx.compose.material3.AlertDialog
            import androidx.compose.ui.graphics.Color

            fun screen() {
                val c = Color(0xFF0F6CBD)
                val i = Icons.Filled
                Toast.makeText(null, "Done", 0)
                AlertDialog(onDismissRequest = {})
                spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
            }
            """.trimIndent(),
        ).expectWarningCount(5)
    }

    @Test
    fun `allows the same calls inside core designsystem and allows safe forms elsewhere`() {
        check(
            """
            package com.devbehindyou.atomicfilemanager.core.designsystem

            import androidx.compose.material.icons.Icons
            import androidx.compose.ui.graphics.Color

            val ink = Color(0xFF15171B)
            val icons = Icons.Default
            """.trimIndent(),
        ).expectClean()
        check(
            """
            package com.devbehindyou.atomicfilemanager.ui.screens

            import androidx.compose.animation.core.Spring
            import androidx.compose.animation.core.spring
            import androidx.compose.material.icons.Icons

            fun screen(value: Long) {
                val outlined = Icons.Outlined
                spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy)
            }
            """.trimIndent(),
        ).expectClean()
    }
}
