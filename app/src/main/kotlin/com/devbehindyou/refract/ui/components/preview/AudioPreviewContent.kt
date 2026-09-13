package com.devbehindyou.refract.ui.components.preview

import android.graphics.Bitmap
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.AppContainer
import com.devbehindyou.refract.data.preview.PreparedMedia
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.ui.util.FileUtils
import kotlinx.coroutines.delay

@Composable
fun AudioPreviewContent(
    node: FileNode,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    var preparedMedia by remember { mutableStateOf<PreparedMedia?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(node.id) {
        isLoading = true
        when (val res = container.mediaPreviewHelper.prepareMedia(node.id, node.name)) {
            is FileResult.Success -> {
                preparedMedia = res.value
                isLoading = false
            }
            is FileResult.Failure -> {
                errorMessage = "Failed to load audio file"
                isLoading = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isLoading -> CircularProgressIndicator()
            errorMessage != null -> Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            preparedMedia != null -> AudioPlayerView(node = node, media = preparedMedia!!)
        }
    }
}

@Composable
private fun AudioPlayerView(
    node: FileNode,
    media: PreparedMedia,
    modifier: Modifier = Modifier,
) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(media.metadata.durationMs) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }

    DisposableEffect(media.filePath) {
        val player =
            MediaPlayer().apply {
                setDataSource(media.filePath)
                prepare()
                val trackDuration = duration.toLong()
                if (trackDuration > 0) durationMs = trackDuration
                setOnCompletionListener {
                    isPlaying = false
                    currentPositionMs = 0L
                }
            }
        mediaPlayer = player
        onDispose {
            try {
                player.stop()
                player.reset()
                player.release()
            } catch (ignored: Exception) {
                // Ignore player disposal errors
            }
            mediaPlayer = null
            media.cleanup()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                if (!isUserSeeking && player.isPlaying) {
                    currentPositionMs = player.currentPosition.toLong()
                }
            }
            delay(250)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AudioArtworkCard(artwork = media.metadata.artwork)
        Spacer(modifier = Modifier.height(24.dp))
        AudioTrackInfo(node = node, media = media)
        Spacer(modifier = Modifier.height(20.dp))
        AudioProgressBar(
            currentMs = if (isUserSeeking) seekPositionMs.toLong() else currentPositionMs,
            totalMs = durationMs,
            sliderValue = if (isUserSeeking) seekPositionMs else currentPositionMs.toFloat(),
            onSeekChange = {
                isUserSeeking = true
                seekPositionMs = it
            },
            onSeekFinished = {
                isUserSeeking = false
                mediaPlayer?.seekTo(seekPositionMs.toInt())
                currentPositionMs = seekPositionMs.toLong()
            },
        )
        Spacer(modifier = Modifier.height(16.dp))
        AudioPlaybackControls(
            isPlaying = isPlaying,
            onTogglePlayPause = {
                val player = mediaPlayer ?: return@AudioPlaybackControls
                if (isPlaying) {
                    player.pause()
                    isPlaying = false
                } else {
                    player.start()
                    isPlaying = true
                }
            },
            onRewind = {
                mediaPlayer?.let { player ->
                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                    currentPositionMs = player.currentPosition.toLong()
                }
            },
            onForward = {
                mediaPlayer?.let { player ->
                    player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                    currentPositionMs = player.currentPosition.toLong()
                }
            },
        )
    }
}

@Composable
private fun AudioArtworkCard(
    artwork: Bitmap?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.size(200.dp),
        tonalElevation = 6.dp,
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork.asImageBitmap(),
                contentDescription = "Album Artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioTrackInfo(
    node: FileNode,
    media: PreparedMedia,
    modifier: Modifier = Modifier,
) {
    val title = media.metadata.title ?: node.displayName
    val artist = media.metadata.artist ?: "Unknown Artist"
    val album = media.metadata.album?.takeIf { it.isNotBlank() }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (album != null) "$artist • $album" else artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AudioProgressBar(
    currentMs: Long,
    totalMs: Long,
    sliderValue: Float,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue.coerceIn(0f, totalMs.toFloat().coerceAtLeast(1f)),
            onValueChange = onSeekChange,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..totalMs.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = FileUtils.formatDuration(currentMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = FileUtils.formatDuration(totalMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AudioPlaybackControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onRewind, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s")
        }
        Spacer(modifier = Modifier.width(16.dp))
        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = onForward, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s")
        }
    }
}
