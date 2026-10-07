package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * One Media3 player for a preview (ALL_IN_ONE_PLAN.md 1.5): prepared for [filePath], paused when the
 * app goes to the background, and released when the preview closes, so audio never keeps playing
 * behind the app. [onRelease] runs after the player is released (for example to delete a temp copy).
 */
@Composable
fun rememberPreviewPlayer(
    filePath: String,
    playWhenReady: Boolean,
    onRelease: () -> Unit,
): ExoPlayer {
    val context = LocalContext.current
    val player =
        remember(filePath) {
            ExoPlayer.Builder(context.applicationContext).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
                this.playWhenReady = playWhenReady
                prepare()
            }
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) player.pause()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
            onRelease()
        }
    }
    return player
}

/** Playback speeds the preview offers, in tap order; pure so it is unit-tested. */
object PlaybackSpeeds {
    private val steps = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

    fun next(current: Float): Float = steps[(steps.indexOfFirst { it == current } + 1).mod(steps.size)]

    fun label(speed: Float): String {
        val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().trimEnd('0')
        return "$text×"
    }
}
