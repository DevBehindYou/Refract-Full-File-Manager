package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class TextContent(
    val lines: List<String>,
    val totalLinesCount: Int,
    val isTruncated: Boolean,
    val totalBytesRead: Long,
)

data class FileChecksums(
    val md5: String,
    val sha256: String,
)

@Singleton
class ReadFileContentUseCase @Inject constructor(
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    suspend fun readText(
        id: FileNodeId,
        maxLines: Int = 5000,
        maxBytes: Long = 1024 * 1024,
    ): FileResult<TextContent> = withContext(Dispatchers.IO) {
        val backend = backendSelector(id)
        val inRes = backend.openInput(id)
        val inProvider = when (inRes) {
            is FileResult.Success -> inRes.value
            is FileResult.Failure -> return@withContext FileResult.Failure(inRes.error)
        }

        try {
            inProvider.stream().use { stream ->
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                val lines = mutableListOf<String>()
                var line: String? = reader.readLine()
                var bytesRead = 0L
                var isTruncated = false

                while (line != null) {
                    bytesRead += line.toByteArray(Charsets.UTF_8).size + 1
                    lines.add(line)
                    if (lines.size >= maxLines || bytesRead >= maxBytes) {
                        isTruncated = true
                        break
                    }
                    line = reader.readLine()
                }

                FileResult.Success(
                    TextContent(
                        lines = lines,
                        totalLinesCount = lines.size,
                        isTruncated = isTruncated,
                        totalBytesRead = bytesRead,
                    )
                )
            }
        } catch (e: Exception) {
            FileResult.Failure(FileError.IoFailure(id.raw))
        }
    }

    suspend fun calculateChecksums(id: FileNodeId): FileResult<FileChecksums> = withContext(Dispatchers.IO) {
        val backend = backendSelector(id)
        val inRes = backend.openInput(id)
        val inProvider = when (inRes) {
            is FileResult.Success -> inRes.value
            is FileResult.Failure -> return@withContext FileResult.Failure(inRes.error)
        }

        try {
            val md5Digest = MessageDigest.getInstance("MD5")
            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)

            inProvider.stream().use { stream ->
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    md5Digest.update(buffer, 0, read)
                    sha256Digest.update(buffer, 0, read)
                }
            }

            fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

            FileResult.Success(
                FileChecksums(
                    md5 = md5Digest.digest().toHex(),
                    sha256 = sha256Digest.digest().toHex(),
                )
            )
        } catch (e: Exception) {
            FileResult.Failure(FileError.IoFailure(id.raw))
        }
    }
}
