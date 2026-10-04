package com.devbehindyou.atomicfilemanager.core.designsystem.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keyboard/D-pad focus ring (spec §11.2): a [width] outline in [color], [gap] outside the
 * element. Drawn over the content so it is never clipped by the element's own background.
 */
fun Modifier.focusRing(
    focused: Boolean,
    color: Color,
    cornerRadius: Dp,
    width: Dp = 2.dp,
    gap: Dp = 2.dp,
): Modifier =
    if (!focused) {
        this
    } else {
        drawWithContent {
            drawContent()
            val inset = gap.toPx() + width.toPx() / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(-inset, -inset),
                size = Size(size.width + inset * 2f, size.height + inset * 2f),
                cornerRadius = CornerRadius(cornerRadius.toPx() + inset),
                style = Stroke(width = width.toPx()),
            )
        }
    }
