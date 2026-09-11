package com.devbehindyou.refract.domain.usecase

import com.devbehindyou.refract.domain.model.CollisionPolicy
import com.devbehindyou.refract.domain.model.FileError
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveOperationsTest {

    private lateinit var backend: InMemoryBackend
    private lateinit var engine: FileOperationsEngine
    private lateinit var inspectArchive: InspectArchiveUseCase
    private lateinit var readContent: ReadFileContentUseCase

    @BeforeEach
    fun setUp() {
        backend = InMemoryBackend()
        engine = FileOperationsEngine { backend }
        inspectArchive = InspectArchiveUseCase { backend }
        readContent = ReadFileContentUseCase { backend }
    }

    private suspend fun writeTextFile(parentId: FileNodeId, name: String, text: String): FileNodeId {
        val target = (backend.openOutput(parentId, name, "text/plain") as FileResult.Success).value
        target.stream().use { it.write(text.toByteArray(Charsets.UTF_8)) }
        target.sync()
        return (target.toNode() as FileResult.Success).value.id
    }

    private suspend fun writeBytesFile(parentId: FileNodeId, name: String, bytes: ByteArray): FileNodeId {
        val target = (backend.openOutput(parentId, name, "application/octet-stream") as FileResult.Success).value
        target.stream().use { it.write(bytes) }
        target.sync()
        return (target.toNode() as FileResult.Success).value.id
    }

    @Test
    fun `compress and inspect archive works end-to-end`() = runTest {
        val file1 = writeTextFile(backend.rootId, "notes.txt", "Hello Refract World!")
        val subDir = (backend.createDirectory(backend.rootId, "docs") as FileResult.Success).value
        writeTextFile(subDir.id, "readme.md", "# Refract Manager")

        val op = FileOperation(
            id = OperationId.random(),
            type = OperationType.COMPRESS,
            sources = listOf(file1, subDir.id),
            destination = backend.rootId,
            options = OperationOptions(),
            createdAt = System.currentTimeMillis(),
        )

        val snapshots = engine.execute(op).toList()
        val finalStatus = snapshots.last().status
        assertTrue(finalStatus is OperationStatus.Completed, "Expected completed compress, got $finalStatus")

        val archiveNodeId = (finalStatus as OperationStatus.Completed).summary.succeeded.first()
        assertTrue(archiveNodeId in listOf(file1, subDir.id))

        // Find the generated archive.zip
        val children = mutableListOf<com.devbehindyou.refract.domain.model.FileNode>()
        backend.listChildren(backend.rootId).collect {
            if (it is FileResult.Success) children.addAll(it.value)
        }
        val zipNode = children.firstOrNull { it.name.endsWith(".zip") }
        assertTrue(zipNode != null, "Zip file should exist in root")

        // Inspect archive entries without extraction
        val entriesRes = inspectArchive(zipNode!!.id)
        assertTrue(entriesRes is FileResult.Success)
        val entries = (entriesRes as FileResult.Success).value
        assertTrue(entries.any { it.path == "notes.txt" })
        assertTrue(entries.any { it.path.contains("docs") })
    }

    @Test
    fun `extract extracts archive files successfully`() = runTest {
        // Create a valid zip directly
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("hello.txt"))
            zos.write("Content of hello".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("sub/folder/nested.txt"))
            zos.write("Nested content".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val zipFileId = writeBytesFile(backend.rootId, "sample.zip", baos.toByteArray())
        val extractDir = (backend.createDirectory(backend.rootId, "extracted") as FileResult.Success).value

        val op = FileOperation(
            id = OperationId.random(),
            type = OperationType.EXTRACT,
            sources = listOf(zipFileId),
            destination = extractDir.id,
            options = OperationOptions(),
            createdAt = System.currentTimeMillis(),
        )

        val snapshots = engine.execute(op).toList()
        val finalStatus = snapshots.last().status
        assertTrue(finalStatus is OperationStatus.Completed, "Expected extraction to complete, got $finalStatus")
    }

    @Test
    fun `zip slip attack is detected and blocked`() = runTest {
        // Create a malicious zip with path traversal
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/passwd"))
            zos.write("malicious payload".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val evilZipId = writeBytesFile(backend.rootId, "evil.zip", baos.toByteArray())
        val extractDir = (backend.createDirectory(backend.rootId, "safe_dir") as FileResult.Success).value

        val op = FileOperation(
            id = OperationId.random(),
            type = OperationType.EXTRACT,
            sources = listOf(evilZipId),
            destination = extractDir.id,
            options = OperationOptions(),
            createdAt = System.currentTimeMillis(),
        )

        val snapshots = engine.execute(op).toList()
        val finalStatus = snapshots.last().status
        assertTrue(finalStatus is OperationStatus.Failed, "Expected Zip Slip to fail operation, got $finalStatus")
        val failed = (finalStatus as OperationStatus.Failed).summary.failed
        assertTrue(failed.isNotEmpty())
        assertTrue(failed.first().error is FileError.SuspiciousArchive)
    }

    @Test
    fun `read text content handles truncation correctly`() = runTest {
        val longText = (1..100).joinToString("\n") { "Line $it: testing text preview functionality" }
        val fileId = writeTextFile(backend.rootId, "log.txt", longText)

        // Read with maxLines = 10
        val result = readContent.readText(fileId, maxLines = 10)
        assertTrue(result is FileResult.Success)
        val content = (result as FileResult.Success).value

        assertEquals(10, content.lines.size)
        assertTrue(content.isTruncated)
    }

    @Test
    fun `checksum calculation produces correct hashes`() = runTest {
        val sample = "Antigravity Refract\n"
        val fileId = writeTextFile(backend.rootId, "hash_test.txt", sample)

        val checksumResult = readContent.calculateChecksums(fileId)
        assertTrue(checksumResult is FileResult.Success)
        val hashes = (checksumResult as FileResult.Success).value

        // Known MD5 of "Antigravity Refract\n"
        val expectedMd5 = java.security.MessageDigest.getInstance("MD5")
            .digest(sample.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expectedMd5, hashes.md5)
        assertFalse(hashes.sha256.isEmpty())
    }
}
