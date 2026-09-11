package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import javax.inject.Inject

class CreateDirectoryUseCase @Inject constructor(
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    suspend operator fun invoke(parent: FileNodeId, name: String): FileResult<FileNode> {
        val backend = backendSelector(parent)
        return backend.createDirectory(parent, name)
    }
}
