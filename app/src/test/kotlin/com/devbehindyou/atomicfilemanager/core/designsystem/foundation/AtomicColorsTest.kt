package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AtomicColorsTest {
    private fun assertContrast(
        label: String,
        foreground: Int,
        background: Int,
        minimum: Double,
    ) {
        val ratio = ColorMath.contrast(foreground, background)
        assertTrue(ratio >= minimum, "$label: contrast %.2f is below %.1f".format(ratio, minimum))
    }

    @Test
    fun contrastMathMatchesTheDesignSystemTable() {
        // Values from ATOMIC_DESIGN_SYSTEM.md §3.6.
        assertEquals(16.39, ColorMath.contrast(AtomicPalette.INK, AtomicPalette.PAPER), 0.05)
        assertEquals(6.74, ColorMath.contrast(AtomicPalette.SIGNAL, AtomicPalette.PAPER), 0.05)
        assertEquals(7.38, ColorMath.contrast(AtomicPalette.WHITE, AtomicPalette.SIGNAL), 0.05)
        assertEquals(2.43, ColorMath.contrast(AtomicPalette.SIGNAL, AtomicPalette.INK), 0.05)
    }

    @Test
    fun lightRolesMeetContrastRules() {
        val c = AtomicColorFactory.light()
        listOf(c.background, c.surfaceCard, c.surfaceInset, c.surfaceRaised).forEach { bg ->
            assertContrast("content", c.content, bg, 4.5)
            assertContrast("secondary", c.contentSecondary, bg, 4.5)
            assertContrast("accent text", c.accent.text, bg, 4.5)
            assertContrast("strong border", c.borderStrong, bg, 3.0)
        }
        assertContrast("on accent", c.accent.onFill, c.accent.fill, 4.5)
        assertContrast("error", c.error.error, c.background, 4.5)
        assertContrast("error container", c.error.onContainer, c.error.container, 4.5)
    }

    @Test
    fun darkRolesMeetContrastRulesAndNeverUseSignalAsText() {
        val c = AtomicColorFactory.dark()
        listOf(c.background, c.surfaceCard, c.surfaceInset, c.surfaceRaised).forEach { bg ->
            assertContrast("content", c.content, bg, 4.5)
            assertContrast("secondary", c.contentSecondary, bg, 4.5)
            assertContrast("muted", c.contentMuted, bg, 4.5)
            assertContrast("accent text", c.accent.text, bg, 4.5)
            assertContrast("strong border", c.borderStrong, bg, 3.0)
        }
        assertContrast("on accent", c.accent.onFill, c.accent.fill, 4.5)
        assertContrast("error", c.error.error, c.background, 4.5)
        assertContrast("error container", c.error.onContainer, c.error.container, 4.5)
        assertTrue(c.accent.text != AtomicPalette.SIGNAL)
    }
}
