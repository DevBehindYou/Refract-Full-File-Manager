package com.devbehindyou.atomicfilemanager.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object FileUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }

    private val isoDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    /** ISO date and 24-hour time in the device's zone, for example "2026-10-04 08:11" (owner decision). */
    fun formatDate(
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (epochMillis <= 0L) return "—"
        return isoDateTime.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
