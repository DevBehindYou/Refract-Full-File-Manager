package com.devbehindyou.refract.data.backend

import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.StorageBackend
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a [FileNodeId] to the [StorageBackend] that can handle it. This is deliberately a
 * **stateless dispatch on `id.prefix`**, not a permission-aware decision — every id already
 * encodes which backend created it (`file:`/`saf:`/`media:`), so routing an *existing* id
 * needs no live permission state at all.
 *
 * The harder question — *which* backend a shared volume's root should get assigned to in
 * the first place, given the current access grant (All Files Access vs. a SAF tree grant vs.
 * neither) — is deliberately **not** this class's job. That's Phase 4's
 * `ResolveStorageAccessUseCase`/`StorageAccessManager`, which doesn't exist yet. Solving it
 * here would create a circular dependency (Phase 3 needing Phase 4's not-yet-built
 * permission logic) that this design avoids by keeping the two concerns separate: this class
 * answers "given an id, which backend reads it," never "given a volume, which backend should
 * its root use."
 */
@Singleton
class StorageBackendSelector
    @Inject
    constructor(
        private val backends: Map<BackendType, @JvmSuppressWildcards StorageBackend>,
    ) {
        fun forNode(id: FileNodeId): StorageBackend {
            val backendType =
                when (id.prefix) {
                    FileNodeId.Prefix.FILE -> BackendType.FILE
                    FileNodeId.Prefix.SAF -> BackendType.SAF
                    FileNodeId.Prefix.MEDIA -> BackendType.MEDIASTORE
                    FileNodeId.Prefix.USB -> BackendType.USB
                    FileNodeId.Prefix.SFTP -> BackendType.SFTP
                    FileNodeId.Prefix.FTP -> BackendType.FTP
                    FileNodeId.Prefix.FTPS -> BackendType.FTPS
                    FileNodeId.Prefix.SMB -> BackendType.SMB
                    FileNodeId.Prefix.WEBDAV -> BackendType.WEBDAV
                    null -> error("Malformed FileNodeId with no recognised prefix: $id")
                }
            return backends[backendType] ?: error("No StorageBackend bound for $backendType")
        }
    }
