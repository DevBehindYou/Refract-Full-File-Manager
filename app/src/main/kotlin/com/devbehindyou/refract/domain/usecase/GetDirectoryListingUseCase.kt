package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
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
