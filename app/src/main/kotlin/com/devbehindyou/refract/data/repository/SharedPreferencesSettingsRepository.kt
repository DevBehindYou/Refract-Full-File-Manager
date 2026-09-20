package com.devbehindyou.refract.data.repository

import android.content.Context
import com.devbehindyou.refract.domain.model.AppSettings
import com.devbehindyou.refract.domain.model.DEFAULT_HIDDEN_FOLDER
import com.devbehindyou.refract.domain.model.HideMode
import com.devbehindyou.refract.domain.model.ThemeMode
import com.devbehindyou.refract.domain.model.normalizeHiddenFolder
import com.devbehindyou.refract.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Settings live in a small SharedPreferences file. They are read once, synchronously, at
 * construction because the theme must be known before the first frame is drawn.
 */
class SharedPreferencesSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        write(state.updateAndGet(transform))
    }

    private fun read(): AppSettings =
        AppSettings(
            themeMode = prefs.getString(KEY_THEME, null).toEnumOrNull<ThemeMode>() ?: ThemeMode.SYSTEM,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            defaultHideMode = prefs.getString(KEY_DEFAULT_HIDE_MODE, null).toEnumOrNull<HideMode>(),
            requireAuthForHidden = prefs.getBoolean(KEY_REQUIRE_AUTH, false),
            showHiddenFiles = prefs.getBoolean(KEY_SHOW_HIDDEN, true),
            hiddenFolder =
                prefs.getString(KEY_HIDDEN_FOLDER, null)?.let(::normalizeHiddenFolder) ?: DEFAULT_HIDDEN_FOLDER,
        )

    private fun write(settings: AppSettings) {
        val editor =
            prefs.edit()
                .putString(KEY_THEME, settings.themeMode.name)
                .putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
                .putBoolean(KEY_REQUIRE_AUTH, settings.requireAuthForHidden)
                .putBoolean(KEY_SHOW_HIDDEN, settings.showHiddenFiles)
                .putString(KEY_HIDDEN_FOLDER, settings.hiddenFolder)
        val hideMode = settings.defaultHideMode
        if (hideMode == null) {
            editor.remove(KEY_DEFAULT_HIDE_MODE)
        } else {
            editor.putString(KEY_DEFAULT_HIDE_MODE, hideMode.name)
        }
        editor.apply()
    }

    private inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    private companion object {
        const val PREFS_NAME = "refract_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_DEFAULT_HIDE_MODE = "default_hide_mode"
        const val KEY_REQUIRE_AUTH = "require_auth_for_hidden"
        const val KEY_SHOW_HIDDEN = "show_hidden_files"
        const val KEY_HIDDEN_FOLDER = "hidden_folder"
    }
}
