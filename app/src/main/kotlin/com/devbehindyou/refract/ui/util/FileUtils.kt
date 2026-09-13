package com.devbehindyou.refract.ui.util

import java.text.SimpleDateFormat
import java.util.Date
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

    fun formatDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return "—"
        val now = System.currentTimeMillis()
        val diff = now - epochMillis
        val oneDay = 24L * 60 * 60 * 1000

        return when {
            diff in 0..oneDay -> {
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Today, " + timeFormat.format(Date(epochMillis))
            }
            diff in oneDay..(2 * oneDay) -> {
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Yesterday, " + timeFormat.format(Date(epochMillis))
            }
            else -> {
                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                dateFormat.format(Date(epochMillis))
            }
        }
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
