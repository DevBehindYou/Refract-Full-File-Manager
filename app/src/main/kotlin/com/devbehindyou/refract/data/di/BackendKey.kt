package com.devbehindyou.refract.data.di

import com.devbehindyou.refract.domain.repository.BackendType
import dagger.MapKey

/**
 * The map key for the `Map<BackendType, StorageBackend>` multibinding
 * (`architecture/ARCHITECTURE.md` §6, verbatim pattern). First real Hilt code in this
 * project beyond Phase 1's bare `@HiltAndroidApp` — KSP-generated code this sandbox cannot
 * compile-check.
 */
@MapKey
annotation class BackendKey(val value: BackendType)
