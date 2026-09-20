package com.devbehindyou.refract.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Preferences that outlive a session.
 *
 * [defaultHideMode] is `null` when the user wants to be asked how to hide each file. When it is set,
 * hiding a file uses that method straight away.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultHideMode: HideMode? = null,
    val requireAuthForHidden: Boolean = false,
    val showHiddenFiles: Boolean = true,
)
