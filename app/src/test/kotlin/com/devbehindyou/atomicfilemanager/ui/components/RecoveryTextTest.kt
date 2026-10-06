package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.usecase.CleanupReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RecoveryTextTest {
    @Test
    fun `headline counts operations`() {
        assertEquals("An operation was cut short", RecoveryText.headline(1))
        assertEquals("3 operations were cut short", RecoveryText.headline(3))
    }

    @Test
    fun `cleanup says what was tidied, or nothing`() {
        assertNull(RecoveryText.cleanup(CleanupReport()))
        assertEquals(
            "Removed 2 unfinished copies; put back 1 file that was being replaced.",
            RecoveryText.cleanup(CleanupReport(removedPartials = 2, restoredOriginals = 1)),
        )
        assertEquals(
            "Put back 2 files that were being replaced.",
            RecoveryText.cleanup(CleanupReport(restoredOriginals = 2)),
        )
    }
}
