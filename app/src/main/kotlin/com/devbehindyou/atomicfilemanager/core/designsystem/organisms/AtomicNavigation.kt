package com.devbehindyou.atomicfilemanager.core.designsystem.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing

/** A top-level destination for the bottom bar or rail. [label] is sentence case. */
data class AtomicDestination(
    val key: String,
    val label: String,
    val icon: ImageVector,
    /** Stable tag for UI tests (for example "tab_home"). */
    val testTag: String? = null,
)

/**
 * Bottom bar (spec §9.6): paper, 1 dp rule on top, exactly one active destination drawn as an
 * ink pill with icon and label; the others are icon-only but keep their labels for TalkBack.
 */
@Composable
fun AtomicBottomBar(
    destinations: List<AtomicDestination>,
    selectedKey: String,
    onSelect: (AtomicDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Box(
            Modifier.fillMaxWidth().heightIn(
                min = AtomicBorder.rule,
                max = AtomicBorder.rule,
            ).background(colors.borderStrong),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = BAR_HEIGHT)
                    .padding(horizontal = AtomicSpacing.s12)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                DestinationItem(destination, destination.key == selectedKey, vertical = false) { onSelect(destination) }
            }
        }
    }
}

/** Navigation rail for medium and expanded widths, same language as [AtomicBottomBar]. */
@Composable
fun AtomicNavRail(
    destinations: List<AtomicDestination>,
    selectedKey: String,
    onSelect: (AtomicDestination) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = Atomic.colors
    Row(modifier.fillMaxHeight().background(colors.background)) {
        Column(
            modifier =
                Modifier
                    .width(RAIL_WIDTH)
                    .fillMaxHeight()
                    .padding(vertical = AtomicSpacing.s16)
                    .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12),
        ) {
            header?.invoke()
            destinations.forEach { destination ->
                DestinationItem(destination, destination.key == selectedKey, vertical = true) { onSelect(destination) }
            }
        }
        Box(Modifier.fillMaxHeight().width(AtomicBorder.rule).background(colors.borderStrong))
    }
}

@Composable
private fun DestinationItem(
    destination: AtomicDestination,
    selected: Boolean,
    vertical: Boolean,
    onClick: () -> Unit,
) {
    val colors = Atomic.colors
    val base =
        Modifier
            .heightIn(min = AtomicSize.touchTarget)
            .widthIn(min = AtomicSize.touchTarget)
            .then(if (destination.testTag != null) Modifier.testTag(destination.testTag) else Modifier)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = destination.label }
    if (!selected) {
        Box(base, contentAlignment = Alignment.Center) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(AtomicSize.icon),
            )
        }
        return
    }
    val pill =
        base
            .background(colors.content, AtomicShape.sm)
            .padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s10)
    val inner: @Composable () -> Unit = {
        Icon(
            destination.icon,
            contentDescription = null,
            tint = colors.background,
            modifier = Modifier.size(AtomicSize.iconSmall),
        )
        AtomicText(destination.label, AtomicTextRole.MonoLabel, color = colors.background, maxLines = 1)
    }
    if (vertical) {
        Column(
            pill,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s4),
        ) {
            inner()
        }
    } else {
        Row(
            pill,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            inner()
        }
    }
}

private val BAR_HEIGHT = AtomicSpacing.s74 - AtomicSpacing.s2
private val RAIL_WIDTH = AtomicSpacing.s74 + AtomicSpacing.s22
