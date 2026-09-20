package com.devbehindyou.refract.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where "Hide from Gallery" puts files, relative to the root of the storage the file is on. */
const val DEFAULT_HIDDEN_FOLDER = "Refract/Hidden"

/**
 * Cleans a folder typed by the user (relative to a storage root). Returns `null` when it cannot be
 * used, for example when it is empty or tries to leave the storage root with `..`.
 */
fun normalizeHiddenFolder(raw: String): String? {
    val parts = raw.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }
    val usable =
        parts.isNotEmpty() &&
            parts.none { part -> part == "." || part == ".." || part.any { it == ':' || it.isISOControl() } }
    return if (usable) parts.joinToString("/") else null
}

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
    val hiddenFolder: String = DEFAULT_HIDDEN_FOLDER,
)
