package com.devbehindyou.atomicfilemanager.core.designsystem.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicBorder
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography
import com.devbehindyou.atomicfilemanager.core.designsystem.modifiers.focusRing

/**
 * Text field (spec §9.5): white field, 2 dp ink border, mono caps label above, and an error
 * sentence below that says how to fix the problem. The value keeps its original case.
 */
@Composable
fun AtomicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /** For passwords: `PasswordVisualTransformation()`. */
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = Atomic.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor =
        when {
            errorText != null -> colors.error
            focused -> colors.accentText
            else -> colors.borderStrong
        }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6)) {
        AtomicText(label, AtomicTextRole.MonoLabel)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (errorText != null) Modifier.semantics { error(errorText) } else Modifier),
            enabled = enabled,
            singleLine = singleLine,
            textStyle = AtomicTypography.body.copy(color = colors.content),
            cursorBrush = SolidColor(colors.accentText),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AtomicSize.touchTarget)
                            .focusRing(focused, colors.accentText, AtomicShape.radiusSm)
                            .background(colors.surfaceCard, AtomicShape.sm)
                            .border(AtomicBorder.control, borderColor, AtomicShape.sm)
                            .padding(horizontal = AtomicSpacing.s12, vertical = AtomicSpacing.s12),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        AtomicText(placeholder, AtomicTextRole.Body, color = colors.contentSecondary, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        if (errorText != null) {
            AtomicText(errorText, AtomicTextRole.BodySecondary, color = colors.error)
        }
    }
}
