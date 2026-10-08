package com.devbehindyou.atomicfilemanager.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnalysisAgeTest {
    @Test
    fun saysNothingBeforeTheFirstScanThenHowLongAgo() {
        assertEquals("", AnalysisAge.suffix(null, 1_000))
        assertEquals(" · scanned just now", AnalysisAge.suffix(1_000, 30_000))
        assertEquals(" · scanned 4 min ago", AnalysisAge.suffix(0, 4 * 60_000 + 59_000))
        // A clock that moved backwards is not shown as a negative age.
        assertEquals(" · scanned just now", AnalysisAge.suffix(10_000, 0))
    }
}
