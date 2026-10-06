package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.ActiveDropTarget
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.DropTargetType
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.FileDragController
import com.devbehindyou.atomicfilemanager.ui.interaction.drag.fileDropTarget

data class BreadcrumbItem(
    val name: String,
    val path: String,
)

/**
 * Mono path breadcrumb (ATOMIC_UI_PLAN.md §7.2): segments in original case, separated by "/",
 * the current folder in ink, earlier ones tappable. Every segment is also a drop target that
 * opens on hover while dragging. Scrolls to the end when the path grows.
 */
@Composable
fun BreadcrumbBar(
    breadcrumbs: List<BreadcrumbItem>,
    onBreadcrumbClick: (BreadcrumbItem) -> Unit,
    modifier: Modifier = Modifier,
    dragController: FileDragController? = null,
) {
    val colors = Atomic.colors
    val scrollState = rememberScrollState()

    LaunchedEffect(breadcrumbs.size) {
        if (breadcrumbs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = AtomicSpacing.s16)
                .testTag("breadcrumb_bar"),
    ) {
        breadcrumbs.forEachIndexed { index, item ->
            val isLast = index == breadcrumbs.lastIndex
            if (index > 0) AtomicText("/", AtomicTextRole.MonoMeta)
            val dropTarget =
                dragController?.let { controller ->
                    FileNodeId.parse(item.path)?.let { targetId ->
                        Modifier.fileDropTarget(
                            controller = controller,
                            target =
                                ActiveDropTarget(
                                    id = item.path,
                                    destinationId = targetId,
                                    type = DropTargetType.BREADCRUMB,
                                    displayName = item.name,
                                    isWritable = true,
                                ),
                            onHoverSpringOpen = { onBreadcrumbClick(item) },
                        )
                    }
                } ?: Modifier
            AtomicText(
                text = item.name,
                role = AtomicTextRole.MonoMeta,
                color = if (isLast) colors.content else colors.contentSecondary,
                maxLines = 1,
                modifier =
                    dropTarget
                        .heightIn(min = AtomicSize.touchTarget)
                        .clickable(enabled = !isLast, role = Role.Button) { onBreadcrumbClick(item) }
                        .padding(vertical = AtomicSpacing.s12),
            )
        }
    }
}
