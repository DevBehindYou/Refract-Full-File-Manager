package com.devbehindyou.refract.data.hide

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

class FastObscureVerificationTest {
    @TempDir
    lateinit var tempDir: File

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `fast obscure and restore produces byte-for-byte identical SHA-256 for small text file`() {
        val file = File(tempDir, "sample.txt")
        file.writeText("Hello Refract! This is a confidential note.")

        val originalBytes = file.readBytes()
        val originalSha = sha256(file)

        // 1. Obscure
        val obscureResult = FastObscureHelper.obscureFile(file)
        assertTrue(obscureResult.isSuccess)
        val obscuredFile = obscureResult.getOrThrow()

        assertTrue(obscuredFile.exists())
        assertTrue(obscuredFile.name.endsWith(FastObscureHelper.OBSCURE_EXTENSION))
        assertFalse(file.exists()) // Original name is gone

        // Verify header was masked
        val obscuredBytes = obscuredFile.readBytes()
        assertFalse(obscuredBytes.take(10).toByteArray().contentEquals(originalBytes.take(10).toByteArray()))

        // 2. Restore
        val restoreResult = FastObscureHelper.restoreFile(obscuredFile)
        assertTrue(restoreResult.isSuccess)
        val restoredFile = restoreResult.getOrThrow()

        assertTrue(restoredFile.exists())
        assertEquals("sample.txt", restoredFile.name)
        assertFalse(obscuredFile.exists())

        // 3. Verify SHA-256 byte-for-byte identical
        val restoredSha = sha256(restoredFile)
        assertEquals(originalSha, restoredSha)
        assertArrayEquals(originalBytes, restoredFile.readBytes())
    }

    @Test
    fun `fast obscure and restore produces byte-for-byte identical SHA-256 for large binary file`() {
        val file = File(tempDir, "video_sample.mp4")
        val randomData = ByteArray(256 * 1024) // 256 KB binary data
        Random(42).nextBytes(randomData)
        file.writeBytes(randomData)

        val originalSha = sha256(file)

        // 1. Obscure
        val obscureResult = FastObscureHelper.obscureFile(file)
        assertTrue(obscureResult.isSuccess)
        val obscuredFile = obscureResult.getOrThrow()

        // 2. Restore
        val restoreResult = FastObscureHelper.restoreFile(obscuredFile)
        assertTrue(restoreResult.isSuccess)
        val restoredFile = restoreResult.getOrThrow()

        // 3. Verify SHA-256
        val restoredSha = sha256(restoredFile)
        assertEquals(originalSha, restoredSha)
        assertEquals(randomData.size.toLong(), restoredFile.length())
    }

    @Test
    fun `obscuring non-existent file returns failure`() {
        val nonExistent = File(tempDir, "missing.png")
        val result = FastObscureHelper.obscureFile(nonExistent)
        assertTrue(result.isFailure)
    }

    @Test
    fun `restoring non-obscured file fails gracefully`() {
        val normalFile = File(tempDir, "regular.txt")
        normalFile.writeText("Not obscured")

        val result = FastObscureHelper.restoreFile(normalFile)
        assertTrue(result.isFailure)
    }

    @Test
    fun `fast obscure and restore round-trips a zero-byte file`() {
        val file = File(tempDir, "empty.bin")
        file.writeBytes(ByteArray(0))

        assertEquals(0L, file.length())
        val originalSha = sha256(file)

        val obscureResult = FastObscureHelper.obscureFile(file)
        assertTrue(obscureResult.isSuccess, "Obscure of 0-byte file should succeed")
        val obscuredFile = obscureResult.getOrThrow()

        assertTrue(obscuredFile.exists())
        assertTrue(obscuredFile.name.endsWith(FastObscureHelper.OBSCURE_EXTENSION))

        val restoreResult = FastObscureHelper.restoreFile(obscuredFile)
        assertTrue(restoreResult.isSuccess, "Restore of 0-byte obscured file should succeed")
        val restoredFile = restoreResult.getOrThrow()

        assertTrue(restoredFile.exists())
        assertEquals("empty.bin", restoredFile.name)
        assertEquals(0L, restoredFile.length())
        assertEquals(originalSha, sha256(restoredFile))
    }

    @Test
    fun `fast obscure and restore round-trips a file exactly at the 512-byte header boundary`() {
        val file = File(tempDir, "exact512.dat")
        val data = ByteArray(512) { i -> (i % 127).toByte() }
        file.writeBytes(data)

        val originalSha = sha256(file)

        val obscureResult = FastObscureHelper.obscureFile(file)
        assertTrue(obscureResult.isSuccess)
        val obscuredFile = obscureResult.getOrThrow()

        val restoreResult = FastObscureHelper.restoreFile(obscuredFile)
        assertTrue(restoreResult.isSuccess)
        val restoredFile = restoreResult.getOrThrow()

        assertEquals(512L, restoredFile.length())
        assertEquals(originalSha, sha256(restoredFile))
    }

    @Test
    fun `fast obscure preserves unicode filename through full round-trip`() {
        val file = File(tempDir, "ファイル_café_документ.txt")
        file.writeText("Unicode filename test content")

        val originalSha = sha256(file)

        val obscureResult = FastObscureHelper.obscureFile(file)
        assertTrue(obscureResult.isSuccess)
        val obscuredFile = obscureResult.getOrThrow()

        val restoreResult = FastObscureHelper.restoreFile(obscuredFile)
        assertTrue(restoreResult.isSuccess)
        val restoredFile = restoreResult.getOrThrow()

        assertEquals("ファイル_café_документ.txt", restoredFile.name)
        assertEquals(originalSha, sha256(restoredFile))
    }
}
