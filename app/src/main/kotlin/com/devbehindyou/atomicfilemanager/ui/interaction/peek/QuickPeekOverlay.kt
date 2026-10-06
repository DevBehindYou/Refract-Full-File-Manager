package com.devbehindyou.atomicfilemanager.ui.interaction.peek

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicElevation
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.hardShadow
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils

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
                val colors = Atomic.colors
                Column(
                    modifier =
                        Modifier
                            .padding(AtomicSpacing.s24)
                            .widthIn(min = PEEK_MIN_WIDTH, max = PEEK_MAX_WIDTH)
                            .hardShadow(AtomicElevation.level5, colors.shadow, AtomicShape.md)
                            .background(colors.surfaceCard, AtomicShape.md)
                            .border(AtomicBorder.structure, colors.borderStrong, AtomicShape.md)
                            .clip(AtomicShape.md)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = PEEK_MEDIA_MIN, max = PEEK_MEDIA_MAX)
                                .background(colors.surfaceInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = controller.previewBitmap
                        when {
                            controller.isLoading -> AtomicLoading("Loading preview…")
                            bitmap != null ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = activeNode.displayName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = PEEK_MEDIA_MAX),
                                )
                            activeNode.mimeType?.startsWith("video/") == true ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
                                    modifier = Modifier.padding(AtomicSpacing.s32),
                                ) {
                                    AtomicIconTile(AtomicIcons.Video)
                                    AtomicText("Video · release to close", AtomicTextRole.MonoLabel)
                                }
                            else -> AtomicIconTile(AtomicIcons.Image)
                        }
                    }
                    AtomicDivider(strong = true)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(AtomicSpacing.s16),
                        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
                    ) {
                        AtomicText(activeNode.displayName, AtomicTextRole.Name, maxLines = 1)
                        AtomicText(
                            listOfNotNull(FileUtils.formatBytes(activeNode.size), controller.resolution)
                                .joinToString(" · "),
                            AtomicTextRole.MonoMeta,
                        )
                    }
                }
            }
        }
    }
}

private val PEEK_MIN_WIDTH = 280.dp
private val PEEK_MAX_WIDTH = 460.dp
private val PEEK_MEDIA_MIN = 200.dp
private val PEEK_MEDIA_MAX = 380.dp
