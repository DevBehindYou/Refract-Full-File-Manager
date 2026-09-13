package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.InputStreamProvider
import com.devbehindyou.refract.domain.repository.StorageBackend
import com.devbehindyou.refract.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStream

class TextPreviewBoundsTest {
    @Test
    fun `a long single line obeys byte limit before reading entire file`() =
        runTest {
            var readCount = 0
            val input =
                object : InputStream() {
                    override fun read(): Int = if (readCount++ < 4 * 1024 * 1024) 'a'.code else -1
                }
            val backend =
                object : StorageBackend by InMemoryBackend() {
                    override suspend fun openInput(id: FileNodeId) = FileResult.Success(InputStreamProvider { input })
                }
            val result = ReadFileContentUseCase { backend }.readText(FileNodeId.file("/large.txt"), maxBytes = 4096)
            assertTrue(result is FileResult.Success && result.value.isTruncated)
            assertTrue(readCount <= 4097, "Read $readCount bytes for a 4096-byte preview")
        }

    @Test
    fun `checksum cancellation is not converted into an IO failure`() =
        runTest {
            val backend =
                object : StorageBackend by InMemoryBackend() {
                    override suspend fun openInput(id: FileNodeId) =
                        FileResult.Success(
                            InputStreamProvider {
                                object : InputStream() {
                                    override fun read(): Int = throw CancellationException("Cancelled read")
                                }
                            },
                        )
                }
            var cancelled = false
            try {
                ReadFileContentUseCase { backend }.calculateChecksums(FileNodeId.file("/cancel.bin"))
            } catch (expected: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        }
}
