package com.devbehindyou.atomicfilemanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.ui.screens.PaletteCommand
import com.devbehindyou.atomicfilemanager.ui.shortcuts.AppShortcuts
import com.devbehindyou.atomicfilemanager.ui.shortcuts.OpenTarget
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

/**
 * Home-screen storage widget (ALL_IN_ONE_PLAN.md 4.5): free space on internal storage and a bar.
 * A tap opens Storage. Refreshed by the system every 30 minutes; it reads one StatFs, nothing else.
 */
class StorageWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val stats = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val total = stats?.totalBytes ?: 0L
        val free = stats?.availableBytes ?: 0L
        val open =
            PendingIntent.getActivity(
                context,
                0,
                AppShortcuts.intent(context, OpenTarget.Command(PaletteCommand.STORAGE)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val views =
            RemoteViews(context.packageName, R.layout.widget_storage).apply {
                setTextViewText(R.id.widget_storage_summary, StorageWidgetText.summary(free, total))
                setProgressBar(
                    R.id.widget_storage_bar,
                    StorageWidgetText.BAR_MAX,
                    StorageWidgetText.usedSteps(free, total),
                    false,
                )
                setOnClickPendingIntent(R.id.widget_storage_root, open)
            }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/** Wording and bar position for [StorageWidget]; pure so it is unit-tested. */
object StorageWidgetText {
    const val BAR_MAX = 100

    fun summary(
        free: Long,
        total: Long,
    ): String =
        if (total <= 0) {
            "Storage unavailable"
        } else {
            "${FileUtils.formatBytes(
                free,
            )} free of ${FileUtils.formatBytes(total)}"
        }

    /** Used share of the bar, 0..[BAR_MAX]; an empty or unreadable volume shows an empty bar. */
    fun usedSteps(
        free: Long,
        total: Long,
    ): Int = if (total <= 0) 0 else (((total - free.coerceIn(0, total)) * BAR_MAX) / total).toInt()
}
