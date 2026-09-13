package com.devbehindyou.refract.ui.interaction.peek

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.devbehindyou.refract.domain.model.FileNode
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Distinguishes stationary hold (Instagram-style Quick Peek) vs tap.
 * Hold past [peekDelayMs] triggers Quick Peek with haptic tick.
 * Releasing finger immediately dismisses Quick Peek.
 */
@Composable
fun Modifier.mediaGestureArbiter(
    node: FileNode,
    peekDelayMs: Long = 350L,
    onTap: () -> Unit,
    onQuickPeek: (FileNode) -> Unit,
    onDismissPeek: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val currentOnTap = rememberUpdatedState(onTap)
    val currentOnQuickPeek = rememberUpdatedState(onQuickPeek)
    val currentOnDismissPeek = rememberUpdatedState(onDismissPeek)

    return this.pointerInput(node.id.raw) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)

            val releasedBeforeHold =
                withTimeoutOrNull(peekDelayMs) {
                    // Cancellation (scroll interception, pointer cancel, leaving bounds) is
                    // distinct from the timeout that intentionally activates Quick Peek.
                    waitForUpOrCancellation() != null
                }

            if (releasedBeforeHold == true) {
                // Tapped before hold duration -> open viewer
                currentOnTap.value()
            } else if (releasedBeforeHold == null) {
                // Held past duration -> Quick Peek!
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnQuickPeek.value(node)

                // Wait until pointer is released to dismiss
                try {
                    waitForUpOrCancellation()
                } finally {
                    currentOnDismissPeek.value()
                }
            }
        }
    }
}
