package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

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
class ReadFileContentUseCase
    @Inject
    constructor(
        private val backendSelector: (FileNodeId) -> StorageBackend,
    ) {
        suspend fun readText(
            id: FileNodeId,
            maxLines: Int = 5000,
            maxBytes: Long = 1024 * 1024,
        ): FileResult<TextContent> =
            withContext(Dispatchers.IO) {
                require(maxLines > 0 && maxBytes > 0)
                val backend = backendSelector(id)
                val inRes = backend.openInput(id)
                val inProvider =
                    when (inRes) {
                        is FileResult.Success -> inRes.value
                        is FileResult.Failure -> return@withContext FileResult.Failure(inRes.error)
                    }

                try {
                    inProvider.stream().use { stream ->
                        val limited = PreviewInputStream(stream, maxBytes)
                        val reader = BufferedReader(InputStreamReader(limited, Charsets.UTF_8))
                        val lines = mutableListOf<String>()
                        var line: String? = reader.readLine()

                        while (line != null && lines.size < maxLines) {
                            coroutineContext.ensureActive()
                            lines.add(line)
                            line = reader.readLine()
                        }
                        val isTruncated = line != null || (limited.bytesRead == maxBytes && stream.read() != -1)

                        FileResult.Success(
                            TextContent(
                                lines = lines,
                                totalLinesCount = lines.size,
                                isTruncated = isTruncated,
                                totalBytesRead = limited.bytesRead,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    FileResult.Failure(FileError.IoFailure(id.raw))
                }
            }

        suspend fun calculateChecksums(id: FileNodeId): FileResult<FileChecksums> =
            withContext(Dispatchers.IO) {
                val backend = backendSelector(id)
                val inRes = backend.openInput(id)
                val inProvider =
                    when (inRes) {
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
                            coroutineContext.ensureActive()
                            md5Digest.update(buffer, 0, read)
                            sha256Digest.update(buffer, 0, read)
                        }
                    }

                    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

                    FileResult.Success(
                        FileChecksums(
                            md5 = md5Digest.digest().toHex(),
                            sha256 = sha256Digest.digest().toHex(),
                        ),
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    FileResult.Failure(FileError.IoFailure(id.raw))
                }
            }
    }

/** Limits bytes before readLine can allocate for an arbitrarily long user-file line. */
private class PreviewInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    var bytesRead = 0L
        private set

    override fun read(): Int {
        if (bytesRead >= limit) return -1
        return `in`.read().also { if (it != -1) bytesRead++ }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (bytesRead >= limit) return -1
        val count = `in`.read(buffer, offset, minOf(length.toLong(), limit - bytesRead).toInt())
        if (count > 0) bytesRead += count
        return count
    }
}
