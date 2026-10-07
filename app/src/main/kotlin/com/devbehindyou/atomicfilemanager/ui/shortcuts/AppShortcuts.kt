package com.devbehindyou.atomicfilemanager.ui.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.devbehindyou.atomicfilemanager.MainActivity
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.ui.screens.PaletteCommand

/** Launcher shortcuts (ALL_IN_ONE_PLAN.md 4.5): long-press the app icon, or pin a folder. */
object AppShortcuts {
    /** The long-press list. Safe to call on every start; the launcher keeps only the latest set. */
    fun publish(context: Context) {
        val shortcuts =
            listOf(
                shortcut(
                    context,
                    "search",
                    "Search",
                    OpenTarget.Command(PaletteCommand.SEARCH),
                    R.drawable.ic_shortcut_search,
                ),
                shortcut(
                    context,
                    "downloads",
                    "Downloads",
                    OpenTarget.Command(PaletteCommand.DOWNLOADS),
                    R.drawable.ic_shortcut_download,
                ),
                shortcut(
                    context,
                    "trash",
                    "Trash",
                    OpenTarget.Command(PaletteCommand.TRASH),
                    R.drawable.ic_shortcut_trash,
                ),
                shortcut(context, "commands", "Commands", OpenTarget.Palette, R.drawable.ic_shortcut_commands),
            )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    fun canPinFolder(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Asks the launcher to add [name] to the home screen; the launcher shows its own confirmation. */
    fun pinFolder(
        context: Context,
        raw: String,
        name: String,
    ): Boolean {
        val info =
            shortcut(
                context,
                "folder-${raw.hashCode()}",
                name.ifBlank {
                    "Folder"
                },
                OpenTarget.Folder(raw),
                R.drawable.ic_shortcut_folder,
            )
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, info, null) }.getOrDefault(false)
    }

    fun intent(
        context: Context,
        target: OpenTarget,
    ): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_OPEN, target.encode())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun shortcut(
        context: Context,
        id: String,
        label: String,
        target: OpenTarget,
        icon: Int,
    ): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent(context, target))
            .build()
}
