package com.devbehindyou.refract.lint

/**
 * Package-prefix constants for Refract's layer boundaries, as named in
 * architecture/MODULES.md and architecture/ARCHITECTURE.md. Centralised here so the
 * seven detectors don't each hardcode these strings independently.
 */
internal object RefractPackages {
    const val ROOT = "com.devbehindyou.refract"
    const val DOMAIN = "$ROOT.domain"
    const val FEATURE = "$ROOT.feature"
    const val CORE_UI = "$ROOT.core.ui"
    const val CORE_DESIGNSYSTEM = "$ROOT.core.designsystem"
}
