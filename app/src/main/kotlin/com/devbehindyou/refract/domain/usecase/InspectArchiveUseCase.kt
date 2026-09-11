package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream
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
class InspectArchiveUseCase @Inject constructor(
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    suspend operator fun invoke(archiveId: FileNodeId): FileResult<List<ArchiveEntryInfo>> = withContext(Dispatchers.IO) {
        val backend = backendSelector(archiveId)
        val inRes = backend.openInput(archiveId)
        val inProvider = when (inRes) {
            is FileResult.Success -> inRes.value
            is FileResult.Failure -> return@withContext FileResult.Failure(inRes.error)
        }

        val entries = mutableListOf<ArchiveEntryInfo>()
        try {
            inProvider.stream().use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.replace('\\', '/').trimStart('/')
                        if (entryName.contains("..") && entryName.split('/').any { it == ".." }) {
                            return@withContext FileResult.Failure(
                                FileError.SuspiciousArchive(entry.name, "Path traversal sequence detected")
                            )
                        }
                        val fileName = entryName.trimEnd('/').substringAfterLast('/')
                        entries.add(
                            ArchiveEntryInfo(
                                name = fileName.ifEmpty { entryName },
                                path = entryName,
                                isDirectory = entry.isDirectory,
                                uncompressedSize = entry.size.coerceAtLeast(0L),
                                compressedSize = entry.compressedSize.coerceAtLeast(0L),
                                modifiedAt = entry.time.coerceAtLeast(0L),
                            )
                        )
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            FileResult.Success(entries)
        } catch (e: Exception) {
            FileResult.Failure(FileError.CorruptedArchive(archiveId.raw))
        }
    }
}
