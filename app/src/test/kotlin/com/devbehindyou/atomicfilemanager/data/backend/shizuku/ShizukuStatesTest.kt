package com.devbehindyou.atomicfilemanager.data.backend.shizuku

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShizukuStatesTest {
    @Test
    fun `off wins, then running, then permission`() {
        assertEquals(ShizukuState.OFF, ShizukuStates.of(enabled = false, running = true, granted = true))
        assertEquals(ShizukuState.NOT_RUNNING, ShizukuStates.of(enabled = true, running = false, granted = true))
        assertEquals(ShizukuState.NEEDS_PERMISSION, ShizukuStates.of(enabled = true, running = true, granted = false))
        assertEquals(ShizukuState.READY, ShizukuStates.of(enabled = true, running = true, granted = true))
    }
}
