package com.devbehindyou.atomicfilemanager.lint

/**
 * Package-prefix constants for the app's layer boundaries, as named in
 * architecture/MODULES.md and architecture/ARCHITECTURE.md. Centralised here so the
 * seven detectors don't each hardcode these strings independently.
 */
internal object AtomicPackages {
    const val ROOT = "com.devbehindyou.atomicfilemanager"
    const val DOMAIN = "$ROOT.domain"
    const val FEATURE = "$ROOT.feature"
    const val CORE_UI = "$ROOT.core.ui"
    const val CORE_DESIGNSYSTEM = "$ROOT.core.designsystem"
}
