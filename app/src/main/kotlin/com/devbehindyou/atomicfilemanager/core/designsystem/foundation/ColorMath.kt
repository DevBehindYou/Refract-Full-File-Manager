package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Small, dependency-free colour helpers on opaque ARGB ints (WCAG 2.x contrast, HSL hue). */
object ColorMath {
    private fun channel(
        color: Int,
        shift: Int,
    ) = (color shr shift) and 0xFF

    fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Linear blend from [from] (t = 0) to [to] (t = 1). */
    fun mix(
        from: Int,
        to: Int,
        t: Float,
    ): Int {
        fun lerp(shift: Int) = (channel(from, shift) + (channel(to, shift) - channel(from, shift)) * t + 0.5f).toInt()
        return rgb(lerp(16), lerp(8), lerp(0))
    }

    fun relativeLuminance(color: Int): Double {
        fun linear(shift: Int): Double {
            val c = channel(color, shift) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }

    fun contrast(
        a: Int,
        b: Int,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** HSL hue in degrees [0, 360) and saturation [0, 1]. */
    fun hueSaturation(color: Int): Pair<Float, Float> {
        val r = channel(color, 16) / 255f
        val g = channel(color, 8) / 255f
        val b = channel(color, 0) / 255f
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val delta = maxC - minC
        if (delta == 0f) return 0f to 0f
        val lightness = (maxC + minC) / 2f
        val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        val hue =
            when (maxC) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
        return ((hue + 360f) % 360f) to saturation.coerceIn(0f, 1f)
    }
}
