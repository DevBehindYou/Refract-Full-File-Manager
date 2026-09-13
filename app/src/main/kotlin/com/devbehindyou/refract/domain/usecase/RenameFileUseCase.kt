package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
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
