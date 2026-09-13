package com.devbehindyou.refract.data.di

import com.devbehindyou.refract.data.backend.FileSystemBackend
import com.devbehindyou.refract.data.backend.MediaStoreBackend
import com.devbehindyou.refract.data.backend.SafBackend
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.StorageBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

/**
 * Binds the three real backends into the `Map<BackendType, StorageBackend>`
 * [com.devbehindyou.refract.data.backend.StorageBackendSelector] consumes
 * (`architecture/ARCHITECTURE.md` §6). `SingletonComponent`, matching that section's scope
 * table for repositories/backends.
 */
@Module
@InstallIn(SingletonComponent::class)
interface BackendModule {
    @Binds
    @IntoMap
    @BackendKey(BackendType.FILE)
    fun bindFileSystemBackend(impl: FileSystemBackend): StorageBackend

    @Binds
    @IntoMap
    @BackendKey(BackendType.SAF)
    fun bindSafBackend(impl: SafBackend): StorageBackend

    @Binds
    @IntoMap
    @BackendKey(BackendType.MEDIASTORE)
    fun bindMediaStoreBackend(impl: MediaStoreBackend): StorageBackend
}
