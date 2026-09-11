package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import javax.inject.Inject

class GetNodeUseCase @Inject constructor(
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    suspend operator fun invoke(id: FileNodeId): FileResult<FileNode> {
        val backend = backendSelector(id)
        return backend.getNode(id)
    }
}
