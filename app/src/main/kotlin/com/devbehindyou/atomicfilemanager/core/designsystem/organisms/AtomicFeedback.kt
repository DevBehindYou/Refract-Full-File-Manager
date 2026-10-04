package com.devbehindyou.atomicfilemanager.core.designsystem.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicElevation
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.hardShadow

/**
 * Snackbar (replaces Toast): an inverted bar with the message and an optional action such as
 * Undo. Uses the accent variant that reads on the inverted background, and is announced.
 */
@Composable
fun AtomicSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Atomic.colors
    val accent = Atomic.accent
    // The bar is inverted: ink on light themes, paper on dark. Pick the accent tone that reads on it.
    val actionColor = Color(if (colors.isDark) accent.light.text else accent.dark.text)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(AtomicSpacing.s16)
                .hardShadow(AtomicElevation.level3, colors.accent, AtomicShape.sm)
                .background(colors.content, AtomicShape.sm)
                .heightIn(min = AtomicSize.touchTarget)
                .padding(start = AtomicSpacing.s16, end = AtomicSpacing.s4)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicText(message, AtomicTextRole.BodySecondary, color = colors.background, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            AtomicButton(
                actionLabel,
                onClick = onAction,
                variant = AtomicButtonVariant.Text,
                contentColor = actionColor,
            )
        }
    }
}

/** Host for [AtomicSnackbar]s; put it in the screen's Scaffold `snackbarHost` slot. */
@Composable
fun AtomicSnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(state, modifier) { data ->
        AtomicSnackbar(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            onAction = data::performAction,
        )
    }
}
