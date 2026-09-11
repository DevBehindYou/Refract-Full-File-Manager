package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import javax.inject.Inject

class DeleteFileUseCase @Inject constructor(
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    suspend operator fun invoke(id: FileNodeId): FileResult<Unit> {
        val backend = backendSelector(id)
        return backend.delete(id)
    }
}
