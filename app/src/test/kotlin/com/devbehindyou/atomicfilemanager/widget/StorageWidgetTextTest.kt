package com.devbehindyou.atomicfilemanager.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StorageWidgetTextTest {
    private val gb = 1024L * 1024 * 1024

    @Test
    fun `summary shows free of total`() {
        assertEquals("Storage unavailable", StorageWidgetText.summary(0, 0))
        assertEquals(true, StorageWidgetText.summary(32 * gb, 128 * gb).contains(" free of "))
    }

    @Test
    fun `bar shows the used share and stays in range`() {
        assertEquals(75, StorageWidgetText.usedSteps(32 * gb, 128 * gb))
        assertEquals(0, StorageWidgetText.usedSteps(128 * gb, 128 * gb))
        assertEquals(100, StorageWidgetText.usedSteps(0, 128 * gb))
        assertEquals(0, StorageWidgetText.usedSteps(-5, 0))
        assertEquals(100, StorageWidgetText.usedSteps(-5, 128 * gb))
        assertEquals(0, StorageWidgetText.usedSteps(200 * gb, 128 * gb))
    }
}
