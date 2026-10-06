package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

/**
 * Raw Atomic palette (docs/design/ATOMIC_DESIGN_SYSTEM.md §3) as ARGB ints.
 * Screens never use these directly; they read semantic roles from [AtomicColorRoles].
 */
internal object AtomicPalette {
    const val INK = 0xFF15171B.toInt()
    const val PAPER = 0xFFF4F5F1.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val SURFACE = 0xFFEDEEE8.toInt()
    const val RAISED = 0xFFF9FAF4.toInt()
    const val SIGNAL = 0xFF3A2FF0.toInt()
    const val SIGNAL_HOVER = 0xFF2A20C9.toInt()
    const val SIGNAL_DEEP = 0xFF1D14A0.toInt()
    const val SIGNAL_LIGHT = 0xFF8F88FF.toInt()
    const val SLATE = 0xFF4A4D55.toInt()
    const val LINE = 0xFFC6C6CB.toInt()
    const val TRACK = 0xFFE8E9E3.toInt()
    const val CARD_DARK = 0xFF1E2026.toInt()
    const val ERROR = 0xFFBA1A1A.toInt()
    const val ERROR_CONTAINER = 0xFFFFDAD6.toInt()
    const val ON_ERROR_CONTAINER = 0xFF93000A.toInt()

    // Dark error family: Material's baseline dark error tones (spec §13.5 keeps error as Material's palette).
    const val ERROR_DARK = 0xFFFFB4AB.toInt()
    const val ON_ERROR_DARK = 0xFF690005.toInt()
    const val ERROR_CONTAINER_DARK = 0xFF93000A.toInt()
    const val ON_ERROR_CONTAINER_DARK = 0xFFFFDAD6.toInt()
    const val ENERGY_HIGH = 0xFFEB7D00.toInt()
    const val LIVE = 0xFF3DDC84.toInt()
}

/** The accent family. Only this group changes when wallpaper colours are on (ATOMIC_UI_PLAN.md §4.5). */
data class AccentSet(
    /** Fill for primary buttons, selected borders, meters. White ([onFill]) text sits on it. */
    val fill: Int,
    val onFill: Int,
    /** Pressed/hover fill, about 15 % darker. */
    val pressed: Int,
    /** Offset-shadow colour under featured items (the spec's signal-deep role). */
    val deep: Int,
    /** Accent used as text or icon colour on this theme's background. */
    val text: Int,
)

/** Semantic colour roles. Pure Kotlin so contrast can be unit-tested; [AtomicColors] wraps them for Compose. */
data class AtomicColorRoles(
    val isDark: Boolean,
    val background: Int,
    /** Things the user owns or acts on (file cards, rows on light). */
    val surfaceCard: Int,
    /** Groups and tools (settings groups, tool tiles). */
    val surfaceInset: Int,
    val surfaceRaised: Int,
    val content: Int,
    val contentSecondary: Int,
    val contentMuted: Int,
    val accent: AccentSet,
    /** Borders the user must see: controls, cards, rules. */
    val borderStrong: Int,
    /** Decorative hairlines only. */
    val borderHair: Int,
    val shadow: Int,
    val error: ErrorSet,
    val meter: MeterSet,
)

data class ErrorSet(val error: Int, val onError: Int, val container: Int, val onContainer: Int)

/** Storage meter (the spec's energy bar): normal fill is the accent, ≥ 80 % used turns [high]. */
data class MeterSet(val normal: Int, val high: Int, val track: Int, val live: Int)

object AtomicColorFactory {
    val signalLight =
        AccentSet(
            fill = AtomicPalette.SIGNAL,
            onFill = AtomicPalette.WHITE,
            pressed = AtomicPalette.SIGNAL_HOVER,
            deep = AtomicPalette.SIGNAL_DEEP,
            text = AtomicPalette.SIGNAL,
        )

    val signalDark = signalLight.copy(text = AtomicPalette.SIGNAL_LIGHT)

    fun light(accent: AccentSet = signalLight): AtomicColorRoles =
        AtomicColorRoles(
            isDark = false,
            background = AtomicPalette.PAPER,
            surfaceCard = AtomicPalette.WHITE,
            surfaceInset = AtomicPalette.SURFACE,
            surfaceRaised = AtomicPalette.RAISED,
            content = AtomicPalette.INK,
            contentSecondary = AtomicPalette.SLATE,
            contentMuted = AtomicPalette.SLATE,
            accent = accent,
            borderStrong = AtomicPalette.INK,
            borderHair = AtomicPalette.LINE,
            shadow = AtomicPalette.INK,
            error =
                ErrorSet(
                    AtomicPalette.ERROR,
                    AtomicPalette.WHITE,
                    AtomicPalette.ERROR_CONTAINER,
                    AtomicPalette.ON_ERROR_CONTAINER,
                ),
            meter = MeterSet(accent.fill, AtomicPalette.ENERGY_HIGH, AtomicPalette.TRACK, AtomicPalette.LIVE),
        )

    /** Atomic Dark (spec §13.9 suggestion; ATOMIC_UI_PLAN.md §4.1). Paper overlays follow spec §3.5. */
    fun dark(accent: AccentSet = signalDark): AtomicColorRoles {
        fun paperOverInk(alpha: Float) = ColorMath.mix(AtomicPalette.INK, AtomicPalette.PAPER, alpha)
        return AtomicColorRoles(
            isDark = true,
            background = AtomicPalette.INK,
            surfaceCard = AtomicPalette.CARD_DARK,
            surfaceInset = paperOverInk(0.05f),
            surfaceRaised = paperOverInk(0.08f),
            content = AtomicPalette.PAPER,
            contentSecondary = paperOverInk(0.78f),
            contentMuted = paperOverInk(0.62f),
            accent = accent,
            borderStrong = paperOverInk(0.6f),
            borderHair = paperOverInk(0.16f),
            // On dark backgrounds the hard shadow is the accent (spec §6.3).
            shadow = accent.fill,
            error =
                ErrorSet(
                    AtomicPalette.ERROR_DARK,
                    AtomicPalette.ON_ERROR_DARK,
                    AtomicPalette.ERROR_CONTAINER_DARK,
                    AtomicPalette.ON_ERROR_CONTAINER_DARK,
                ),
            meter = MeterSet(accent.text, AtomicPalette.ENERGY_HIGH, paperOverInk(0.16f), AtomicPalette.LIVE),
        )
    }
}
