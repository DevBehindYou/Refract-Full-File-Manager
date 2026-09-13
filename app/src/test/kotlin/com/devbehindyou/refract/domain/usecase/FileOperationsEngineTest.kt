package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.CollisionPolicy
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileOperation
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.OperationId
import com.devbehindyou.refract.domain.model.OperationOptions
import com.devbehindyou.refract.domain.model.OperationStatus
import com.devbehindyou.refract.domain.model.OperationType
import com.devbehindyou.refract.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class FileOperationsEngineTest {
    private lateinit var backend: InMemoryBackend
    private lateinit var engine: FileOperationsEngine

    @BeforeEach
    fun setUp() {
        backend = InMemoryBackend()
        engine = FileOperationsEngine { backend }
    }

    private suspend fun createTextFile(
        backend: InMemoryBackend,
        parentId: FileNodeId,
        name: String,
        content: String,
    ): FileNodeId {
        val target = (backend.openOutput(parentId, name, "text/plain") as FileResult.Success).value
        target.stream().use { it.write(content.toByteArray(Charsets.UTF_8)) }
        target.sync()
        return (target.toNode() as FileResult.Success).value.id
    }

    private suspend fun readTextFile(
        backend: InMemoryBackend,
        id: FileNodeId,
    ): String {
        val input = (backend.openInput(id) as FileResult.Success).value
        val baos = ByteArrayOutputStream()
        input.stream().use { it.copyTo(baos) }
        return baos.toString(Charsets.UTF_8)
    }

    private suspend fun getChildren(parentId: FileNodeId): List<FileNode> {
        val children = mutableListOf<FileNode>()
        backend.listChildren(parentId).collect {
            if (it is FileResult.Success) children.addAll(it.value)
        }
        return children
    }

    @Test
    fun `copy single file within same backend succeeds`() =
        runTest {
            val targetFolder = (backend.createDirectory(backend.rootId, "target") as FileResult.Success).value
            val fileId = createTextFile(backend, backend.rootId, "hello.txt", "Hello World")

            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.COPY,
                    sources = listOf(fileId),
                    destination = targetFolder.id,
                    options = OperationOptions(collisionPolicy = CollisionPolicy.OVERWRITE),
                    createdAt = System.currentTimeMillis(),
                )

            val snapshots = engine.execute(op).toList()
            val lastStatus = snapshots.last().status
            assertTrue(lastStatus is OperationStatus.Completed, "Expected Completed, was $lastStatus")
            val summary = (lastStatus as OperationStatus.Completed).summary
            assertEquals(1, summary.succeeded.size)
            assertEquals(fileId, summary.succeeded[0])

            // Verify source still exists
            assertTrue(backend.getNode(fileId) is FileResult.Success)

            // Verify copied file exists in target directory
            val listing = getChildren(targetFolder.id)
            assertEquals(1, listing.size)
            assertEquals("hello.txt", listing[0].name)
            assertEquals("Hello World", readTextFile(backend, listing[0].id))
        }

    @Test
    fun `copy directory recursively copies all nested files and folders`() =
        runTest {
            val srcDir = (backend.createDirectory(backend.rootId, "myDir") as FileResult.Success).value
            val nestedDir = (backend.createDirectory(srcDir.id, "subDir") as FileResult.Success).value
            createTextFile(backend, srcDir.id, "root_file.txt", "Root file")
            createTextFile(backend, nestedDir.id, "sub_file.txt", "Sub file")

            val targetFolder = (backend.createDirectory(backend.rootId, "destDir") as FileResult.Success).value

            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.COPY,
                    sources = listOf(srcDir.id),
                    destination = targetFolder.id,
                    options = OperationOptions(collisionPolicy = CollisionPolicy.OVERWRITE),
                    createdAt = System.currentTimeMillis(),
                )

            val snapshots = engine.execute(op).toList()
            val lastStatus = snapshots.last().status
            assertTrue(lastStatus is OperationStatus.Completed, "Expected Completed, was $lastStatus")

            val destListing = getChildren(targetFolder.id)
            assertEquals(1, destListing.size)
            assertEquals("myDir", destListing[0].name)

            val copiedDirListing = getChildren(destListing[0].id)
            assertEquals(2, copiedDirListing.size)
            val copiedSubDir = copiedDirListing.first { it.isDirectory }
            assertEquals("subDir", copiedSubDir.name)
            val copiedSubListing = getChildren(copiedSubDir.id)
            assertEquals(1, copiedSubListing.size)
            assertEquals("sub_file.txt", copiedSubListing[0].name)
        }

    @Test
    fun `move single file within same backend updates parent and moves item`() =
        runTest {
            val targetFolder = (backend.createDirectory(backend.rootId, "target") as FileResult.Success).value
            val fileId = createTextFile(backend, backend.rootId, "move_me.txt", "Move content")

            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.MOVE,
                    sources = listOf(fileId),
                    destination = targetFolder.id,
                    options = OperationOptions(),
                    createdAt = System.currentTimeMillis(),
                )

            val snapshots = engine.execute(op).toList()
            val lastStatus = snapshots.last().status
            assertTrue(lastStatus is OperationStatus.Completed, "Expected Completed, was $lastStatus")

            // In same-backend atomic move, file is moved to targetFolder:
            val rootListing = getChildren(backend.rootId)
            assertFalse(rootListing.any { it.name == "move_me.txt" }, "File should no longer be in root")

            val targetListing = getChildren(targetFolder.id)
            assertEquals(1, targetListing.size)
            assertEquals("move_me.txt", targetListing[0].name)
            assertEquals(targetFolder.id, (backend.getNode(fileId) as FileResult.Success).value.parentId)
            assertEquals("Move content", readTextFile(backend, fileId))
        }

    @Test
    fun `move cross-backend deletes source and creates at destination`() =
        runTest {
            val otherBackend = InMemoryBackend()
            val fileId = createTextFile(backend, backend.rootId, "cross_move.txt", "Cross backend data")

            val crossEngine =
                FileOperationsEngine { id ->
                    if (id == otherBackend.rootId) otherBackend else backend
                }

            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.MOVE,
                    sources = listOf(fileId),
                    destination = otherBackend.rootId,
                    options = OperationOptions(),
                    createdAt = System.currentTimeMillis(),
                )

            val snapshots = crossEngine.execute(op).toList()
            val lastStatus = snapshots.last().status
            assertTrue(lastStatus is OperationStatus.Completed, "Expected Completed, was $lastStatus")

            // Source file was deleted after cross-backend copy
            assertFalse(backend.getNode(fileId) is FileResult.Success, "Source should be deleted")

            // Dest file exists in otherBackend
            val otherChildren = mutableListOf<FileNode>()
            otherBackend.listChildren(otherBackend.rootId).collect {
                if (it is FileResult.Success) otherChildren.addAll(it.value)
            }
            assertEquals(1, otherChildren.size)
            assertEquals("cross_move.txt", otherChildren[0].name)
        }

    @Test
    fun `delete single file removes file from backend`() =
        runTest {
            val fileId = createTextFile(backend, backend.rootId, "delete_me.txt", "Delete content")

            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.DELETE,
                    sources = listOf(fileId),
                    destination = null,
                    options = OperationOptions(),
                    createdAt = System.currentTimeMillis(),
                )

            val snapshots = engine.execute(op).toList()
            val lastStatus = snapshots.last().status
            assertTrue(lastStatus is OperationStatus.Completed, "Expected Completed, was $lastStatus")
            val summary = (lastStatus as OperationStatus.Completed).summary
            assertEquals(1, summary.succeeded.size)
            assertFalse(backend.getNode(fileId) is FileResult.Success)
        }

    @Test
    fun `delete directory recursively removes all contents`() =
        runTest {
            val dir = (backend.createDirectory(backend.rootId, "to_delete") as FileResult.Success).value
            createTextFile(backend, dir.id, "inner.txt", "Inner")

            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.DELETE,
                    sources = listOf(dir.id),
                    destination = null,
                    options = OperationOptions(),
                    createdAt = System.currentTimeMillis(),
                )

            val snapshots = engine.execute(op).toList()
            val lastStatus = snapshots.last().status
            assertTrue(lastStatus is OperationStatus.Completed, "Expected Completed, was $lastStatus")
            assertFalse(backend.getNode(dir.id) is FileResult.Success)
        }

    @Test
    fun `collision policy SKIP does not overwrite existing file`() =
        runTest {
            val targetFolder = (backend.createDirectory(backend.rootId, "target") as FileResult.Success).value
            val fileId = createTextFile(backend, backend.rootId, "conflict.txt", "New version")
            createTextFile(backend, targetFolder.id, "conflict.txt", "Old version")

            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.COPY,
                    sources = listOf(fileId),
                    destination = targetFolder.id,
                    options = OperationOptions(collisionPolicy = CollisionPolicy.SKIP),
                    createdAt = System.currentTimeMillis(),
                )

            val snapshots = engine.execute(op).toList()
            val lastStatus = snapshots.last().status
            assertTrue(
                lastStatus is OperationStatus.Completed || lastStatus is OperationStatus.PartiallyCompleted,
                "Expected Completed or PartiallyCompleted, was $lastStatus",
            )
            val summary =
                when (lastStatus) {
                    is OperationStatus.Completed -> lastStatus.summary
                    is OperationStatus.PartiallyCompleted -> lastStatus.summary
                    else -> error("Unexpected status")
                }
            assertEquals(1, summary.skipped.size)
            assertEquals(fileId, summary.skipped[0])

            // Verify destination file retained original content
            val listing = getChildren(targetFolder.id)
            assertEquals(1, listing.size)
            assertEquals("Old version", readTextFile(backend, listing[0].id))
        }

    @Test
    fun `collision policy OVERWRITE replaces existing file content`() =
        runTest {
            val targetFolder = (backend.createDirectory(backend.rootId, "target") as FileResult.Success).value
            val fileId = createTextFile(backend, backend.rootId, "replace.txt", "Brand new content")
            createTextFile(backend, targetFolder.id, "replace.txt", "Stale content")

            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.COPY,
                    sources = listOf(fileId),
                    destination = targetFolder.id,
                    options = OperationOptions(collisionPolicy = CollisionPolicy.OVERWRITE),
                    createdAt = System.currentTimeMillis(),
                )

            val snapshots = engine.execute(op).toList()
            val lastStatus = snapshots.last().status
            assertTrue(lastStatus is OperationStatus.Completed, "Expected Completed, was $lastStatus")

            val listing = getChildren(targetFolder.id)
            assertEquals(1, listing.size)
            assertEquals("Brand new content", readTextFile(backend, listing[0].id))
        }

    @Test
    fun `non-existent source reports failure gracefully`() =
        runTest {
            val badId = FileNodeId("file:/non/existent/path/ghost.txt")

            val op =
                FileOperation(
                    id = OperationId.random(),
                    type = OperationType.COPY,
                    sources = listOf(badId),
                    destination = backend.rootId,
                    options = OperationOptions(),
                    createdAt = System.currentTimeMillis(),
                )

            val snapshots = engine.execute(op).toList()
            val lastStatus = snapshots.last().status
            assertTrue(lastStatus is OperationStatus.Failed, "Expected Failed, was $lastStatus")
            val summary = (lastStatus as OperationStatus.Failed).summary
            assertEquals(1, summary.failed.size)
            assertEquals(badId, summary.failed[0].id)
        }
}
