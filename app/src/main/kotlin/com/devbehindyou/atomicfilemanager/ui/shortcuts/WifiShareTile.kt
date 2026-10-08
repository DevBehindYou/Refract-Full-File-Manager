package com.devbehindyou.atomicfilemanager.ui.shortcuts

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService
import com.devbehindyou.atomicfilemanager.ui.screens.PaletteCommand

/**
 * Quick Settings tile that opens Wi-Fi share (ALL_IN_ONE_PLAN.md 4.5). It only opens the screen;
 * sharing still needs a tap on Start there, so the tile can never expose files by itself.
 */
class WifiShareTile : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = AppShortcuts.intent(this, OpenTarget.Command(PaletteCommand.WIFI_SHARE))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
