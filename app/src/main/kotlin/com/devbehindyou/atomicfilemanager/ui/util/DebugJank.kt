package com.devbehindyou.atomicfilemanager.ui.util

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.metrics.performance.JankStats

/**
 * Logs janky frames in debug builds (ALL_IN_ONE_PLAN.md §16.1), so phone checks can spot slow
 * screens with `adb logcat -s AtomicJank`. Release builds never track frames.
 */
object DebugJank {
    private const val TAG = "AtomicJank"
    private const val NANOS_PER_MS = 1_000_000

    /** Call after `setContent`, once the window has a decor view. */
    fun track(activity: Activity) {
        if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        JankStats.createAndTrack(activity.window) { frame ->
            if (frame.isJank) {
                Log.w(TAG, "${frame.frameDurationUiNanos / NANOS_PER_MS} ms frame ${frame.states}")
            }
        }
    }
}
