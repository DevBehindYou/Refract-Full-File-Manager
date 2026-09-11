package com.devbehindyou.refract.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.ui.interaction.drag.ActiveDropTarget
import com.devbehindyou.refract.ui.interaction.drag.DropTargetType
import com.devbehindyou.refract.ui.interaction.drag.FileDragController
import com.devbehindyou.refract.ui.interaction.drag.fileDropTarget

data class BreadcrumbItem(
    val name: String,
    val path: String,
)

@Composable
fun BreadcrumbBar(
    breadcrumbs: List<BreadcrumbItem>,
    onBreadcrumbClick: (BreadcrumbItem) -> Unit,
    modifier: Modifier = Modifier,
    dragController: FileDragController? = null,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(breadcrumbs.size) {
        if (breadcrumbs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("breadcrumb_bar")
    ) {
        val rootTargetMod = if (dragController != null && breadcrumbs.isNotEmpty()) {
            FileNodeId.parse(breadcrumbs.first().path)?.let { targetId ->
                Modifier.fileDropTarget(
                    controller = dragController,
                    target = ActiveDropTarget(
                        id = breadcrumbs.first().path,
                        destinationId = targetId,
                        type = DropTargetType.BREADCRUMB,
                        displayName = "Storage",
                        isWritable = true,
                    ),
                    onHoverSpringOpen = { onBreadcrumbClick(breadcrumbs.first()) },
                )
            } ?: Modifier
        } else Modifier

        SuggestionChip(
            modifier = rootTargetMod,
            onClick = {
                if (breadcrumbs.isNotEmpty()) {
                    onBreadcrumbClick(breadcrumbs.first())
                }
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Root",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Storage")
                }
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )

        breadcrumbs.drop(1).forEach { item ->
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(18.dp)
            )

            val isLast = item == breadcrumbs.last()
            val chipTargetMod = if (dragController != null) {
                FileNodeId.parse(item.path)?.let { targetId ->
                    Modifier.fileDropTarget(
                        controller = dragController,
                        target = ActiveDropTarget(
                            id = item.path,
                            destinationId = targetId,
                            type = DropTargetType.BREADCRUMB,
                            displayName = item.name,
                            isWritable = true,
                        ),
                        onHoverSpringOpen = { onBreadcrumbClick(item) },
                    )
                } ?: Modifier
            } else Modifier

            SuggestionChip(
                modifier = chipTargetMod,
                onClick = { onBreadcrumbClick(item) },
                label = {
                    Text(
                        text = item.name,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (isLast) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    labelColor = if (isLast) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            )
        }
    }
}
