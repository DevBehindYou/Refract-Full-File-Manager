package com.devbehindyou.atomicfilemanager.data.vault

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.random.Random

class VaultFilesTest {
    @TempDir
    lateinit var dir: File

    private fun newAead(): StreamingAead {
        StreamingAeadConfig.register()
        return KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
            .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
    }

    private val aead = newAead()
    private val vault = VaultFiles { aead }
    private val id = "item-1".toByteArray()

    private fun decrypt(
        file: File,
        withVault: VaultFiles = vault,
        ad: ByteArray = id,
    ): Result<ByteArray> {
        val out = ByteArrayOutputStream()
        return withVault.decrypt(file, out, ad).map { out.toByteArray() }
    }

    @Test
    fun `a multi-segment file round trips and leaves no temporary file`() {
        val plain = Random(7).nextBytes(3 * 1024 * 1024 + 123)
        val target = File(dir, "vault/a.bin")

        val size = vault.encrypt(plain.inputStream(), target, id).getOrThrow()

        assertEquals(target.length(), size)
        assertFalse(plain.contentEquals(target.readBytes()))
        assertArrayEquals(plain, decrypt(target).getOrThrow())
        assertEquals(listOf("a.bin"), target.parentFile.list()!!.toList())
    }

    @Test
    fun `wrong key, other record or tampering fail instead of returning garbage`() {
        val target = File(dir, "b.bin")
        vault.encrypt("secret notes".toByteArray().inputStream(), target, id).getOrThrow()

        assertTrue(decrypt(target, VaultFiles { newAead() }).isFailure)
        assertTrue(decrypt(target, ad = "item-2".toByteArray()).isFailure)
        val bytes = target.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
        target.writeBytes(bytes)
        assertTrue(decrypt(target).isFailure)
    }

    @Test
    fun `a source that breaks mid-read leaves nothing behind`() {
        val target = File(dir, "c.bin")
        val failing =
            object : InputStream() {
                private var left = 200_000

                override fun read(): Int = if (left-- > 0) 1 else throw java.io.IOException("card removed")
            }

        assertTrue(vault.encrypt(failing, target, id).isFailure)
        assertFalse(target.exists())
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `an existing target is never overwritten`() {
        val target = File(dir, "d.bin").apply { writeText("keep") }

        assertTrue(vault.encrypt("new".toByteArray().inputStream(), target, id).isFailure)
        assertEquals("keep", target.readText())
        assertEquals(listOf("d.bin"), dir.list()!!.toList())
    }
}
