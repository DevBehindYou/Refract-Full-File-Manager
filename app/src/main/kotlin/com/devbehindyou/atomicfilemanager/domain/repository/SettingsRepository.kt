package com.devbehindyou.atomicfilemanager.domain.repository

import com.devbehindyou.atomicfilemanager.domain.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    /** Applies [transform] to the current settings and persists the result. */
    fun update(transform: (AppSettings) -> AppSettings)
}
