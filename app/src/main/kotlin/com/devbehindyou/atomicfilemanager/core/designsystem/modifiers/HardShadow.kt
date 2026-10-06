package com.devbehindyou.atomicfilemanager.core.designsystem.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

/**
 * Atomic elevation: a solid copy of [shape] offset down and right by [offset] (spec §6.3).
 * No blur and no RenderEffect, so it costs one filled shape per frame on every API level.
 * Keep `elevation = 0` on anything that uses it.
 */
fun Modifier.hardShadow(
    offset: Dp,
    color: Color,
    shape: Shape,
): Modifier =
    drawBehind {
        val shift = offset.toPx()
        translate(left = shift, top = shift) {
            drawOutline(shape.createOutline(size, layoutDirection, this), color)
        }
    }
