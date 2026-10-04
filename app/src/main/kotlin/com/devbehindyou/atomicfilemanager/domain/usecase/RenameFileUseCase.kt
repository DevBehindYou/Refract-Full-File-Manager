package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import javax.inject.Inject

class RenameFileUseCase
    @Inject
    constructor(
        private val backendSelector: (FileNodeId) -> StorageBackend,
    ) {
        suspend operator fun invoke(
            id: FileNodeId,
            newName: String,
        ): FileResult<FileNode> {
            val backend = backendSelector(id)
            return backend.rename(id, newName)
        }
    }
