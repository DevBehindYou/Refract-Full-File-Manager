package com.devbehindyou.refract.ui.components.preview

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.devbehindyou.refract.AppContainer
import com.devbehindyou.refract.data.preview.PreparedMedia
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.ui.util.FileUtils

@Composable
fun VideoPreviewContent(
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
                errorMessage = "Failed to load video file"
                isLoading = false
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(color = Color.White)
            errorMessage != null -> Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            preparedMedia != null -> VideoPlayerView(media = preparedMedia!!)
        }
    }
}

@Composable
private fun VideoPlayerView(
    media: PreparedMedia,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(media.filePath) {
        onDispose {
            media.cleanup()
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    val controller = MediaController(context)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoPath(media.filePath)
                    setOnPreparedListener { player ->
                        player.isLooping = false
                        start()
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (media.metadata.width > 0 && media.metadata.height > 0) {
            VideoMetadataBadge(
                width = media.metadata.width,
                height = media.metadata.height,
                durationMs = media.metadata.durationMs,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
            )
        }
    }
}

@Composable
private fun VideoMetadataBadge(
    width: Int,
    height: Int,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.65f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$width×$height • ${FileUtils.formatDuration(durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
