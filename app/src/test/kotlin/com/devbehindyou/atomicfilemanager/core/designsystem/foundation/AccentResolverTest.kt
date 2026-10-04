package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AccentResolverTest {
    private fun hex(value: String) = (0xFF000000 or value.removePrefix("#").toLong(16)).toInt()

    @ParameterizedTest(name = "{0} is accepted")
    @CsvSource(
        "mid blue, #1E5BD8, #AFC6FF",
        "dark green, #1B5E20, #A5D6A7",
        "pale lavender, #E8DEF8, #CFBCFF",
        "pale yellow-green, #DDE7A0, #C5D07A",
        "teal, #00696D, #4CDADA",
    )
    fun acceptedAccentsMeetEveryContrastRule(
        name: String,
        light: String,
        dark: String,
    ) {
        val result = AccentResolver.resolve(hex(light), hex(dark))
        assertEquals(AccentSource.WALLPAPER, result.source, name)
        assertTrue(AccentResolver.passesLight(result.light.fill), "$name light fill")
        assertTrue(ColorMath.contrast(result.dark.text, AtomicPalette.INK) >= 4.5, "$name dark text")
        assertTrue(ColorMath.contrast(AtomicPalette.WHITE, result.dark.fill) >= 4.5, "$name dark fill keeps white text")
    }

    @ParameterizedTest(name = "{0} falls back to Signal")
    @CsvSource(
        "red, #D32F2F, #FFB4AB, RESERVED_HUE",
        "orange, #E65100, #FFB68A, RESERVED_HUE",
        "plum, #601D49, #FFAEDB, RESERVED_HUE",
        "grey, #777777, #C7C7C7, TOO_GREY",
    )
    fun reservedOrGreyWallpaperColoursFallBackToSignal(
        name: String,
        light: String,
        dark: String,
        reason: AccentRejection,
    ) {
        val result = AccentResolver.resolve(hex(light), hex(dark))
        assertEquals(AccentSource.WALLPAPER_REJECTED, result.source, name)
        assertEquals(reason, result.rejection, name)
        assertEquals(AtomicPalette.SIGNAL, result.light.fill, name)
    }
}
