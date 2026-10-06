package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class TextEditingTest {
    private val backend = InMemoryBackend()
    private val editor = TextFileEditor({ backend }, newToken = { "t" })

    private suspend fun file(
        name: String,
        bytes: ByteArray,
    ): FileNode {
        val id = backend.putFile(backend.rootId, name, bytes, "text/plain")
        return (backend.getNode(id) as FileResult.Success).value
    }

    private suspend fun names(): List<String> =
        (backend.listChildren(backend.rootId).first() as FileResult.Success).value.map { it.name }.sorted()

    private suspend fun bytesOf(name: String): ByteArray {
        val node = (backend.listChildren(backend.rootId).first() as FileResult.Success).value.single { it.name == name }
        val out = ByteArrayOutputStream()
        (backend.openInput(node.id) as FileResult.Success).value.stream().use { it.copyTo(out) }
        return out.toByteArray()
    }

    @Test
    fun `line endings and byte order marks survive a round trip`() {
        val crlf = "one\r\ntwo\r\n".toByteArray()
        val decoded = TextCodec.decode(crlf)!!
        assertEquals("one\ntwo\n", decoded.text)
        assertEquals(TextFormat(TextEncoding.UTF8, LineEnding.CRLF), decoded.format)
        assertArrayEquals(crlf, TextCodec.encode(decoded.text, decoded.format))

        val bom = TextEncoding.UTF8_BOM.bom + "héllo".toByteArray()
        val withBom = TextCodec.decode(bom)!!
        assertEquals("héllo", withBom.text)
        assertArrayEquals(bom, TextCodec.encode(withBom.text, withBom.format))

        val utf16 = TextEncoding.UTF16LE.bom + "hi\n".toByteArray(Charsets.UTF_16LE)
        assertEquals("hi\n", TextCodec.decode(utf16)!!.text)
    }

    @Test
    fun `bytes that are not utf-8 are kept byte for byte, binary is refused`() {
        val latin = byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte())
        val decoded = TextCodec.decode(latin)!!
        assertEquals(TextEncoding.LATIN1, decoded.format.encoding)
        assertArrayEquals(latin, TextCodec.encode(decoded.text, decoded.format))

        assertNull(TextCodec.decode(byteArrayOf(0x50, 0x4B, 0x00, 0x03)))
        assertEquals(LineEnding.LF, TextCodec.detectLineEnding("no breaks"))
        assertEquals(LineEnding.CR, TextCodec.detectLineEnding("a\rb\rc"))
    }

    @Test
    fun `find returns every match without overlap`() {
        assertEquals(listOf(0..1, 4..5), TextCodec.findAll("abcdAB", "ab"))
        assertEquals(listOf(0..1), TextCodec.findAll("abcdAB", "ab", ignoreCase = false))
        assertEquals(listOf(0..1, 2..3), TextCodec.findAll("aaaa", "aa"))
        assertTrue(TextCodec.findAll("abc", "").isEmpty())
    }

    @Test
    fun `save replaces the text keeping format and leaves no temporary files`() =
        runTest {
            val node = file("notes.txt", "old\r\nline\r\n".toByteArray())
            val opened = (editor.open(node) as FileResult.Success).value

            val saved = editor.save(node, opened.text.replace("old", "new"), opened.format)

            assertTrue(saved is FileResult.Success, "was $saved")
            assertEquals(listOf("notes.txt"), names())
            assertArrayEquals("new\r\nline\r\n".toByteArray(), bytesOf("notes.txt"))
        }

    @Test
    fun `a failed swap puts the original back and drops the new text`() =
        runTest {
            val node = file("notes.txt", "keep".toByteArray())
            // The temporary file can't take the original's name; everything else works.
            val stubborn =
                object : StorageBackend by backend {
                    override suspend fun rename(
                        id: FileNodeId,
                        newName: String,
                    ): FileResult<FileNode> {
                        val current = (backend.getNode(id) as FileResult.Success).value.name
                        return if (current.startsWith(".atomic-save")) {
                            FileResult.Failure(FileError.AccessDenied(current))
                        } else {
                            backend.rename(id, newName)
                        }
                    }
                }

            val result =
                TextFileEditor({
                    stubborn
                }, newToken = { "t" }).save(node, "new", TextFormat(TextEncoding.UTF8, LineEnding.LF))

            assertTrue(result is FileResult.Failure)
            assertEquals(listOf("notes.txt"), names())
            assertArrayEquals("keep".toByteArray(), bytesOf("notes.txt"))
        }

    @Test
    fun `files over the edit limit are refused`() =
        runTest {
            val node = file("big.log", "x".toByteArray()).copy(size = TextFileEditor.MAX_EDIT_BYTES + 1)

            val result = editor.open(node)

            assertTrue((result as FileResult.Failure).error is FileError.FileTooLarge)
        }
}
