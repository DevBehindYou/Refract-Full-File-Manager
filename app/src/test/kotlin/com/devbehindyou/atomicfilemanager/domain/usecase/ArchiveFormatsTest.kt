package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.data.archive.TempFileSpool
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
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
import java.util.Base64
import net.lingala.zip4j.io.outputstream.ZipOutputStream as Zip4jOutputStream

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
        return ArchiveOperationsHelper.extractArchive(archive, target, backend, backend, {}, {}, spool)
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

    @Test
    fun `7z lists and extracts through a temporary copy`() =
        runTest {
            val file = java.io.File.createTempFile("fixture", ".7z")
            SevenZOutputFile(file).use { out ->
                listOf("photos/a.txt" to "alpha", "b.txt" to "beta").forEach { (path, text) ->
                    val data = text.toByteArray()
                    out.putArchiveEntry(
                        out.createArchiveEntry(java.io.File(path), path).apply { size = data.size.toLong() },
                    )
                    out.write(data)
                    out.closeArchiveEntry()
                }
            }
            val bytes = file.readBytes().also { file.delete() }
            val tempsBefore = sevenZTemps()

            val id = backend.putFile(backend.rootId, "pack.7z", bytes)
            val listed = (InspectArchiveUseCase(spool = spool) { backend }(id) as FileResult.Success).value
            assertEquals(listOf("photos/a.txt", "b.txt"), listed.map { it.path })
            assertEquals(FileOperationsEngine.ItemResult.Success, extract("copy.7z", bytes))
            assertEquals("alpha", textAt("out", "photos", "a.txt"))
            assertEquals(tempsBefore, sevenZTemps())
        }

    @Test
    fun `a password-protected zip says so instead of claiming damage`() =
        runTest {
            val result = extract("locked.zip", Base64.getDecoder().decode(ENCRYPTED_ZIP))

            val error = (result as FileOperationsEngine.ItemResult.Failure).error
            assertEquals(FileError.UnsupportedFormat(PASSWORD_PROTECTED), error)
        }

    @Test
    fun `rar is recognised and a damaged one reports damage`() =
        runTest {
            assertEquals(ArchiveFormats.Kind.RAR, ArchiveFormats.kindOf("show.RAR"))
            assertEquals(ArchiveFormats.Kind.SEVEN_Z, ArchiveFormats.kindOf("backup.7z"))

            val result = extract("broken.rar", "not a rar at all".toByteArray())

            assertTrue((result as FileOperationsEngine.ItemResult.Failure).error is FileError.CorruptedArchive)
        }

    @Test
    fun `the right password lists and extracts, a wrong one says so, and it is used only once`() =
        runTest {
            val bytes = Base64.getDecoder().decode(ENCRYPTED_ZIP)
            val id = backend.putFile(backend.rootId, "locked.zip", bytes)
            val inspect = InspectArchiveUseCase { backend }

            assertEquals(
                FileError.UnsupportedFormat(WRONG_PASSWORD),
                (inspect(id, "nope".toCharArray()) as FileResult.Failure).error,
            )
            assertEquals(listOf("s.txt"), (inspect(id, "pw".toCharArray()) as FileResult.Success).value.map { it.path })

            val target = (backend.createDirectory(backend.rootId, "out") as FileResult.Success).value.id
            ArchivePasswords.put(id, "pw".toCharArray())
            assertEquals(
                FileOperationsEngine.ItemResult.Success,
                ArchiveOperationsHelper.extractArchive(id, target, backend, backend, {}, {}),
            )
            assertEquals("secret", textAt("out", "s.txt"))
            assertEquals(null, ArchivePasswords.take(id))
        }

    @Test
    fun `aes zips open with their password`() =
        runTest {
            val out = ByteArrayOutputStream()
            val params =
                ZipParameters().apply {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    fileNameInZip = "notes.txt"
                }
            Zip4jOutputStream(out, "s3cret".toCharArray()).use { zip ->
                zip.putNextEntry(params)
                zip.write("aes content".toByteArray())
                zip.closeEntry()
            }
            val id = backend.putFile(backend.rootId, "aes.zip", out.toByteArray())

            assertEquals(
                FileError.UnsupportedFormat(PASSWORD_PROTECTED),
                (InspectArchiveUseCase { backend }(id) as FileResult.Failure).error,
            )
            val listed = (InspectArchiveUseCase { backend }(id, "s3cret".toCharArray()) as FileResult.Success).value
            assertEquals(listOf("notes.txt"), listed.map { it.path })
        }

    private val spoolDir = java.nio.file.Files.createTempDirectory("spool").toFile()
    private val spool = TempFileSpool(spoolDir)

    private fun sevenZTemps(): Set<String> =
        spoolDir.list()
            .orEmpty()
            .filter { it.startsWith("atomic-7z-") }
            .toSet()

    private companion object {
        /** `zip -P pw` of one file "s.txt" containing "secret" (traditional ZIP encryption). */
        const val ENCRYPTED_ZIP =
            "UEsDBAoACQAAAEhBR13l6KJcEgAAAAYAAAAFABwAcy50eHRVVAkAA2j+xWpo/sVqdXgLAAEEAAAAAAQAAAAAZCKCJO9TsJ9+" +
                "O6YCATDQhqV6UEsHCOXoolwSAAAABgAAAFBLAQIeAwoACQAAAEhBR13l6KJcEgAAAAYAAAAFABgAAAAAAAEAAACkgQAA" +
                "AABzLnR4dFVUBQADaP7FanV4CwABBAAAAAAEAAAAAFBLBQYAAAAAAQABAEsAAABhAAAAAAA="
    }
}
