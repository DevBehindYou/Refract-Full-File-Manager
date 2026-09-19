package com.devbehindyou.refract.ui.interaction.peek

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.data.preview.ImagePreviewHelper
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.ui.util.FileUtils

@Composable
fun QuickPeekOverlay(
    controller: QuickPeekController,
    imagePreviewHelper: ImagePreviewHelper,
    modifier: Modifier = Modifier,
) {
    val activeNode = controller.activeNode

    LaunchedEffect(activeNode?.id) {
        if (activeNode != null && controller.previewBitmap == null) {
            val isImage = activeNode.mimeType?.startsWith("image/") == true
            if (isImage) {
                when (val res = imagePreviewHelper.decodeImage(activeNode.id, maxDimension = 1280)) {
                    is FileResult.Success -> {
                        val bitmap = res.value
                        controller.setPreview(bitmap, "${bitmap.width} × ${bitmap.height}")
                    }
                    is FileResult.Failure -> {
                        controller.setPreview(null)
                    }
                }
            } else {
                // For videos or other media, set without bitmap (or thumbnail when decoded)
                controller.setPreview(null, "Video Preview")
            }
        }
    }

    AnimatedVisibility(
        visible = controller.isPeeking,
        enter = fadeIn() + scaleIn(initialScale = 0.90f),
        exit = fadeOut() + scaleOut(targetScale = 0.90f),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { controller.dismiss() },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (activeNode != null) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier =
                        Modifier
                            .padding(24.dp)
                            .widthIn(min = 280.dp, max = 460.dp)
                            .clip(RoundedCornerShape(24.dp)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        // Media Display Area
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp, max = 380.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentAlignment = Alignment.Center,
                        ) {
                            val bitmap = controller.previewBitmap
                            when {
                                controller.isLoading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                bitmap != null -> {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = activeNode.displayName,
                                        contentScale = ContentScale.Fit,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 380.dp),
                                    )
                                }
                                activeNode.mimeType?.startsWith("video/") == true -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(64.dp),
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Video Preview (Muted)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(64.dp),
                                    )
                                }
                            }
                        }

                        // Bottom Metadata Bar
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            Text(
                                text = activeNode.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = FileUtils.formatBytes(activeNode.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                controller.resolution?.let { res ->
                                    Text(
                                        text = " • $res",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
