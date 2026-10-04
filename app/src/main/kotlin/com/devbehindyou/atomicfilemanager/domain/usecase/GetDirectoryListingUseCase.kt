package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDirectoryListingUseCase
    @Inject
    constructor(
        private val backendSelector: (FileNodeId) -> StorageBackend,
    ) {
        operator fun invoke(id: FileNodeId): Flow<FileResult<List<FileNode>>> {
            val backend = backendSelector(id)
            return backend.listChildren(id)
        }
    }
