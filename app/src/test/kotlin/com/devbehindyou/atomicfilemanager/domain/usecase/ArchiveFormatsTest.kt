package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class ArchiveFormatsTest {
    private val backend = InMemoryBackend()

    private fun tar(
        entries: Map<String, String>,
        wrap: (OutputStream) -> OutputStream = { it },
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(wrap(bytes)).use { tar ->
            entries.forEach { (path, text) ->
                val data = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(path).apply { size = data.size.toLong() })
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }

    private suspend fun extract(
        name: String,
        bytes: ByteArray,
    ): FileOperationsEngine.ItemResult {
        val archive = backend.putFile(backend.rootId, name, bytes)
        val target = (backend.createDirectory(backend.rootId, "out") as FileResult.Success).value.id
        return ArchiveOperationsHelper.extractArchive(archive, target, backend, backend, {}, {})
    }

    private suspend fun textAt(vararg path: String): String {
        var folder = backend.rootId
        for (segment in path.dropLast(1)) folder = child(folder, segment)
        val file = child(folder, path.last())
        val out = ByteArrayOutputStream()
        (backend.openInput(file) as FileResult.Success).value.stream().use { it.copyTo(out) }
        return out.toString(Charsets.UTF_8)
    }

    private suspend fun child(
        parent: FileNodeId,
        name: String,
    ): FileNodeId = (backend.listChildren(parent).first() as FileResult.Success).value.single { it.name == name }.id

    @Test
    fun `kinds come from the longest matching suffix`() {
        assertEquals(ArchiveFormats.Kind.TAR_GZ, ArchiveFormats.kindOf("backup.TAR.GZ"))
        assertEquals(ArchiveFormats.Kind.TAR_GZ, ArchiveFormats.kindOf("site.tgz"))
        assertEquals(ArchiveFormats.Kind.GZ, ArchiveFormats.kindOf("access.log.gz"))
        assertEquals(ArchiveFormats.Kind.TAR_XZ, ArchiveFormats.kindOf("src.tar.xz"))
        assertEquals(ArchiveFormats.Kind.ZIP, ArchiveFormats.kindOf("photos.zip"))
        assertNull(ArchiveFormats.kindOf("notes.txt"))
        assertNull(ArchiveFormats.kindOf(".gz"))
        assertEquals("access.log", ArchiveFormats.innerName("access.log.gz", ArchiveFormats.Kind.GZ))
    }

    @Test
    fun `tar gz, bz2 and xz list their entries and extract with folders`() =
        runTest {
            val content = mapOf("docs/readme.txt" to "hello", "top.txt" to "top")
            val variants =
                mapOf(
                    "a.tar.gz" to tar(content) { GzipCompressorOutputStream(it) },
                    "b.tar.bz2" to tar(content) { BZip2CompressorOutputStream(it) },
                    "c.tar.xz" to tar(content) { XZCompressorOutputStream(it) },
                )
            for ((name, bytes) in variants) {
                val id = backend.putFile(backend.rootId, name, bytes)
                val listed = (InspectArchiveUseCase { backend }(id) as FileResult.Success).value
                assertEquals(listOf("docs/readme.txt", "top.txt"), listed.map { it.path }, name)
            }

            assertEquals(FileOperationsEngine.ItemResult.Success, extract("d.tgz", variants.getValue("a.tar.gz")))
            assertEquals("hello", textAt("out", "docs", "readme.txt"))
            assertEquals("top", textAt("out", "top.txt"))
        }

    @Test
    fun `a lone gz file extracts to its inner name`() =
        runTest {
            val bytes =
                ByteArrayOutputStream().also {
                        out ->
                    GzipCompressorOutputStream(out).use { it.write("log line".toByteArray()) }
                }

            assertEquals(FileOperationsEngine.ItemResult.Success, extract("app.log.gz", bytes.toByteArray()))
            assertEquals("log line", textAt("out", "app.log"))
        }

    @Test
    fun `entries that climb out of the target are refused`() =
        runTest {
            val result = extract("evil.tar", tar(mapOf("../outside.txt" to "x")))

            assertTrue(
                (result as FileOperationsEngine.ItemResult.Failure).error is FileError.SuspiciousArchive,
                "was $result",
            )
            assertTrue(escapesTarget("a\\..\\..\\b"))
        }
}
