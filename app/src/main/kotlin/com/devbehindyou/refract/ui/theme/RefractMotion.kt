package com.devbehindyou.refract.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Standard motion specifications for Refract adhering to Material 3 motion tokens.
 * Plain Material 3 without custom shaders or unneeded blur passes.
 */
object RefractMotion {
    /** Quick and responsive spring for tactile pick-up, badge pop, and toggle triggers. */
    val Quick: AnimationSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    /** Standard spring for item insertions, layout rearrangements, and drawer expansions. */
    val Standard: AnimationSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        )

    /** Emphasized spring for dialog reveals, sheets, and Quick Peek card popups. */
    val Emphasized: AnimationSpec<Float> =
        spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessLow,
        )

    /** Gentle spring for subtle ambient state shifts and drag drop resets. */
    val Gentle: AnimationSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow,
        )

    /** Standard fading transition tween. */
    val FadeTween = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
}
