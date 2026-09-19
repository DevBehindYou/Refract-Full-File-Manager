package com.devbehindyou.refract.ui.interaction.bubble

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BubbleDockTest {
    @Test
    fun releaseDocksToNearestEdge() {
        assertEquals(0f, bubbleDockEdge(0.2f, 0f, 800f))
        assertEquals(1f, bubbleDockEdge(0.8f, 0f, 800f))
    }

    @Test
    fun throwingAcrossScreenUsesReleaseVelocity() {
        assertEquals(1f, bubbleDockEdge(0.2f, 2000f, 800f))
        assertEquals(0f, bubbleDockEdge(0.8f, -2000f, 800f))
        assertEquals(1f, bubbleDockEdge(1f, 1000f, 0f))
    }
}
