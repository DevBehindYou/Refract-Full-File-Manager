package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ArchiveEntryInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val modifiedAt: Long,
)

@Singleton
class InspectArchiveUseCase
    @Inject
    constructor(
        private val backendSelector: (FileNodeId) -> StorageBackend,
    ) {
        suspend operator fun invoke(archiveId: FileNodeId): FileResult<List<ArchiveEntryInfo>> =
            withContext(Dispatchers.IO) {
                val backend = backendSelector(archiveId)
                val inRes = backend.openInput(archiveId)
                val inProvider =
                    when (inRes) {
                        is FileResult.Success -> inRes.value
                        is FileResult.Failure -> return@withContext FileResult.Failure(inRes.error)
                    }

                val node = (backend.getNode(archiveId) as? FileResult.Success)?.value
                val name = node?.name ?: archiveId.raw.substringAfterLast('/')
                // Anything not recognised by name is read as ZIP, as before.
                val kind = ArchiveFormats.kindOf(name) ?: ArchiveFormats.Kind.ZIP
                val entries = mutableListOf<ArchiveEntryInfo>()
                try {
                    inProvider.stream().use { stream ->
                        ArchiveFormats.open(kind, name, stream, node?.size ?: -1).use { archive ->
                            var header = archive.next()
                            while (header != null) {
                                if (escapesTarget(header.path)) {
                                    return@withContext FileResult.Failure(
                                        FileError.SuspiciousArchive(header.path, "Path traversal sequence detected"),
                                    )
                                }
                                if (entries.size >= ArchiveFormats.MAX_ENTRIES) {
                                    return@withContext FileResult.Failure(
                                        FileError.SuspiciousArchive(
                                            name,
                                            "More than ${ArchiveFormats.MAX_ENTRIES} entries",
                                        ),
                                    )
                                }
                                val entryName = header.path.replace('\\', '/').trimStart('/')
                                val fileName = entryName.trimEnd('/').substringAfterLast('/')
                                entries.add(
                                    ArchiveEntryInfo(
                                        name = fileName.ifEmpty { entryName },
                                        path = entryName,
                                        isDirectory = header.isDirectory,
                                        uncompressedSize = header.size.coerceAtLeast(0L),
                                        compressedSize = header.compressedSize.coerceAtLeast(0L),
                                        modifiedAt = header.modifiedAt.coerceAtLeast(0L),
                                    ),
                                )
                                header = archive.next()
                            }
                        }
                    }
                    FileResult.Success(entries)
                } catch (e: Exception) {
                    FileResult.Failure(FileError.CorruptedArchive(archiveId.raw))
                }
            }
    }
