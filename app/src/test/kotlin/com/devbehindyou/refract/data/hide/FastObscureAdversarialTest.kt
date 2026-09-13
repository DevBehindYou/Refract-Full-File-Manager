package com.devbehindyou.refract.data.hide

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.RandomAccessFile

class FastObscureAdversarialTest {
    @TempDir
    lateinit var directory: File

    private fun original(): File = File(directory, "photo.bin").apply { writeBytes(ByteArray(1024) { it.toByte() }) }

    @Test
    fun `restore collision preserves both files and recoverable footer`() {
        val file = original()
        val hidden = FastObscureHelper.obscureFile(file).getOrThrow()
        val recoverable = hidden.readBytes()
        file.writeText("Do not overwrite")
        assertTrue(FastObscureHelper.restoreFile(hidden).isFailure)
        assertArrayEquals("Do not overwrite".toByteArray(), file.readBytes())
        assertArrayEquals(recoverable, hidden.readBytes())
    }

    @Test
    fun `obscure collision leaves original untouched`() {
        val file = original()
        val bytes = file.readBytes()
        File(directory, file.name + FastObscureHelper.OBSCURE_EXTENSION).writeText("Existing hidden file")
        assertTrue(FastObscureHelper.obscureFile(file).isFailure)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `corrupted footer is rejected before any write`() {
        val hidden = FastObscureHelper.obscureFile(original()).getOrThrow()
        RandomAccessFile(hidden, "rw").use {
            it.seek(it.length() - 5)
            it.writeByte(0)
        }
        val damaged = hidden.readBytes()
        assertTrue(FastObscureHelper.restoreFile(hidden).isFailure)
        assertArrayEquals(damaged, hidden.readBytes())
    }

    @Test
    fun `forged original length cannot expand or truncate restored file`() {
        val hidden = FastObscureHelper.obscureFile(original()).getOrThrow()
        RandomAccessFile(hidden, "rw").use {
            it.seek(it.length() - 4 - "REFRACT_OBSCURE_V1".length - 8)
            it.writeLong(1)
        }
        val damaged = hidden.readBytes()
        assertTrue(FastObscureHelper.restoreFile(hidden).isFailure)
        assertArrayEquals(damaged, hidden.readBytes())
    }

    @Test
    fun `obscuring already obscured file is rejected`() {
        val hidden = FastObscureHelper.obscureFile(original()).getOrThrow()
        val bytes = hidden.readBytes()
        assertTrue(FastObscureHelper.obscureFile(hidden).isFailure)
        assertArrayEquals(bytes, hidden.readBytes())
    }
}
