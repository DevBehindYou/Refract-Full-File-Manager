package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.CollisionPolicy
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileOperation
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.OperationId
import com.devbehindyou.refract.domain.model.OperationOptions
import com.devbehindyou.refract.domain.model.OperationStatus
import com.devbehindyou.refract.domain.model.OperationType
import com.devbehindyou.refract.domain.repository.InputStreamProvider
import com.devbehindyou.refract.domain.repository.OutputTarget
import com.devbehindyou.refract.domain.repository.StorageBackend
import com.devbehindyou.refract.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream

/** End-to-end domain regressions: faults pass through the real operation engine. */
class VerificationRegressionTest {
    private val source = InMemoryBackend()
    private val destination = InMemoryBackend()
    private val bytes = ByteArray(150_000) { (it % 251).toByte() }

    private fun operation(
        id: FileNodeId,
        parent: FileNodeId,
        move: Boolean = true,
    ) = FileOperation(
        id = OperationId.random(),
        type = if (move) OperationType.MOVE else OperationType.COPY,
        sources = listOf(id),
        destination = parent,
        options = OperationOptions(collisionPolicy = CollisionPolicy.OVERWRITE),
        createdAt = 0,
    )

    private suspend fun transfer(
        id: FileNodeId,
        target: StorageBackend,
        origin: StorageBackend = source,
    ): OperationStatus =
        FileOperationsEngine { if (it == destination.rootId) target else origin }
            .execute(operation(id, destination.rootId)).toList().last().status

    private fun failingOutput(cancel: Boolean = false): StorageBackend =
        object : StorageBackend by destination {
            override suspend fun openOutput(
                parent: FileNodeId,
                name: String,
                mime: String?,
            ): FileResult<OutputTarget> {
                val delegate = (destination.openOutput(parent, name, mime) as FileResult.Success).value
                return FileResult.Success(
                    object : OutputTarget by delegate {
                        override fun stream(): OutputStream {
                            val output = delegate.stream()
                            return object : OutputStream() {
                                override fun write(value: Int) {
                                    output.write(value)
                                    if (cancel) throw CancellationException("Injected cancellation")
                                    throw IOException("Injected disk full")
                                }

                                override fun close() = output.close()
                            }
                        }
                    },
                )
            }
        }

    @Test
    fun `same size destination corruption preserves move source`() =
        runTest {
            val id = source.putFile(source.rootId, "photo.bin", bytes)
            val corrupt =
                object : StorageBackend by destination {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                        FileResult.Success(InputStreamProvider { ByteArrayInputStream(ByteArray(bytes.size)) })
                }
            assertTrue(transfer(id, corrupt) is OperationStatus.Failed)
            assertTrue(source.getNode(id) is FileResult.Success)
            assertFalse(destination.exists(destination.rootId, "photo.bin"))
        }

    @Test
    fun `source deletion failure reports failure and preserves verified destination`() =
        runTest {
            val id = source.putFile(source.rootId, "photo.bin", bytes)
            val denied =
                object : StorageBackend by source {
                    override suspend fun delete(id: FileNodeId): FileResult<Unit> =
                        FileResult.Failure(FileError.AccessDenied("Injected delete failure"))
                }
            assertTrue(transfer(id, destination, denied) is OperationStatus.Failed)
            assertTrue(source.getNode(id) is FileResult.Success)
            assertTrue(destination.exists(destination.rootId, "photo.bin"))
        }

    @Test
    fun `failed overwrite preserves both original source and old destination bytes`() =
        runTest {
            val id = source.putFile(source.rootId, "photo.bin", bytes)
            val old = destination.putFile(destination.rootId, "photo.bin", "old content".toByteArray())
            assertTrue(transfer(id, failingOutput()) is OperationStatus.Failed)
            assertTrue(source.getNode(id) is FileResult.Success)
            val input = (destination.openInput(old) as FileResult.Success).value
            assertEquals("old content", input.stream().use { it.readBytes().decodeToString() })
        }

    @Test
    fun `disk full removes partial output and preserves source`() =
        runTest {
            val id = source.putFile(source.rootId, "photo.bin", bytes)
            assertTrue(transfer(id, failingOutput()) is OperationStatus.Failed)
            assertTrue(source.getNode(id) is FileResult.Success)
            assertTrue(children(destination, destination.rootId).isEmpty())
        }

    @Test
    fun `cancellation removes partial output and preserves source`() =
        runTest {
            val id = source.putFile(source.rootId, "photo.bin", bytes)
            var cancelled = false
            try {
                transfer(id, failingOutput(cancel = true))
            } catch (expected: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
            assertTrue(source.getNode(id) is FileResult.Success)
            assertTrue(children(destination, destination.rootId).isEmpty())
        }

    @Test
    fun `copy onto itself is rejected without truncating source`() =
        runTest {
            val id = source.putFile(source.rootId, "photo.bin", bytes)
            val result = FileOperationsEngine { source }.execute(operation(id, source.rootId, move = false)).toList()
            assertTrue(result.last().status is OperationStatus.Failed)
            assertEquals(bytes.size.toLong(), (source.getNode(id) as FileResult.Success).value.size)
        }

    @Test
    fun `moving directory into its descendant is rejected`() =
        runTest {
            val folder = (source.createDirectory(source.rootId, "parent") as FileResult.Success).value
            val child = (source.createDirectory(folder.id, "child") as FileResult.Success).value
            val result = FileOperationsEngine { source }.execute(operation(folder.id, child.id)).toList()
            assertTrue(result.last().status is OperationStatus.Failed)
            assertEquals(source.rootId, (source.getNode(folder.id) as FileResult.Success).value.parentId)
        }

    @Test
    fun `copy in same folder with rename policy preserves original and creates duplicate`() =
        runTest {
            val id = source.putFile(source.rootId, "photo.bin", bytes)
            val request =
                operation(id, source.rootId, move = false).copy(
                    options = OperationOptions(collisionPolicy = CollisionPolicy.RENAME_AUTO),
                )
            val status = FileOperationsEngine { source }.execute(request).toList().last().status
            assertTrue(status is OperationStatus.Completed)
            assertTrue(source.exists(source.rootId, "photo.bin"))
            assertTrue(source.exists(source.rootId, "photo (1).bin"))
        }

    @Test
    fun `storage scan consumes every listing chunk in progress and results`() =
        runTest {
            repeat(451) { source.putFile(source.rootId, "$it.bin", "$it".toByteArray()) }
            val chunked =
                object : StorageBackend by source {
                    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
                        flow {
                            children(source, id).chunked(200).forEach { emit(FileResult.Success(it)) }
                        }
                }
            val analyzer = StorageAnalyzerUseCase({ chunked }, ReadFileContentUseCase { chunked })
            assertEquals(451, analyzer.getFullAnalysis(source.rootId, 1).largeFiles.size)
            assertEquals(451, analyzer.analyze(source.rootId, 1).toList().last().scannedFilesCount)
        }

    @Test
    fun `empty first chunk does not classify nonempty folder as empty`() =
        runTest {
            val folder = (source.createDirectory(source.rootId, "folder") as FileResult.Success).value
            source.putFile(folder.id, "file.bin", bytes)
            val chunked =
                object : StorageBackend by source {
                    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
                        flow {
                            emit(FileResult.Success(emptyList()))
                            emit(FileResult.Success(children(source, id)))
                        }
                }
            val analyzer = StorageAnalyzerUseCase({ chunked }, ReadFileContentUseCase { chunked })
            val result = analyzer.getFullAnalysis(source.rootId, 1)
            assertTrue(result.emptyFolders.isEmpty())
            assertEquals(1, result.largeFiles.size)
        }

    @Test
    fun `cyclic provider listing does not duplicate scanned files`() =
        runTest {
            source.putFile(source.rootId, "file.bin", bytes)
            val cyclic =
                object : StorageBackend by source {
                    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
                        flow {
                            emit(
                                FileResult.Success(
                                    children(source, id) + (source.getNode(source.rootId) as FileResult.Success).value,
                                ),
                            )
                        }
                }
            val analyzer = StorageAnalyzerUseCase({ cyclic }, ReadFileContentUseCase { cyclic })
            assertEquals(1, analyzer.getFullAnalysis(source.rootId, 1).largeFiles.size)
        }

    private suspend fun children(
        backend: StorageBackend,
        parent: FileNodeId,
    ): List<FileNode> = backend.listChildren(parent).toList().flatMap { (it as FileResult.Success).value }
}
