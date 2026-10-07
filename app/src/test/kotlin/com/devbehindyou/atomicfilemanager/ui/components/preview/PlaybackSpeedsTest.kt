package com.devbehindyou.atomicfilemanager.ui.components.preview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackSpeedsTest {
    @Test
    fun `speeds cycle in tap order and back to normal`() {
        val seen = generateSequence(1f) { PlaybackSpeeds.next(it) }.take(6).toList()
        assertEquals(listOf(1f, 1.25f, 1.5f, 2f, 0.75f, 1f), seen)
        assertEquals(1f, PlaybackSpeeds.next(3f))
    }

    @Test
    fun `labels drop needless decimals`() {
        assertEquals("1×", PlaybackSpeeds.label(1f))
        assertEquals("1.25×", PlaybackSpeeds.label(1.25f))
        assertEquals("0.75×", PlaybackSpeeds.label(0.75f))
        assertEquals("2×", PlaybackSpeeds.label(2f))
    }
}
