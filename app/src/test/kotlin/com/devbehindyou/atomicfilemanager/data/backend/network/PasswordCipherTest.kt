package com.devbehindyou.atomicfilemanager.data.backend.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.KeyGenerator

class PasswordCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmPasswordCipher { key }

    @Test
    fun `round trip, including non-ASCII and empty passwords`() {
        listOf("hunter2", "pässwörd 🔑", "").forEach { assertEquals(it, cipher.decrypt(cipher.encrypt(it))) }
    }

    @Test
    fun `every value gets its own IV`() {
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun `a changed or truncated value reads as null, not garbage`() {
        val stored = cipher.encrypt("hunter2")
        val bytes = Base64.getDecoder().decode(stored.removePrefix("v2:"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        assertNull(cipher.decrypt("v2:" + Base64.getEncoder().encodeToString(bytes)))
        assertNull(cipher.decrypt("v2:AAAA"))
        assertNull(cipher.decrypt("v2:not base64!"))
    }

    @Test
    fun `another key can't read it`() {
        val other = AesGcmPasswordCipher { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
        assertNull(other.decrypt(cipher.encrypt("hunter2")))
    }

    @Test
    fun `old values are recognised as needing migration`() {
        assertTrue(cipher.isCurrent(cipher.encrypt("x")))
        assertFalse(cipher.isCurrent("plain"))
        assertNull(cipher.decrypt("plain"))
    }

    @Test
    fun `the legacy reader decrypts the old format and passes plain text through`() {
        val legacy = LegacyPasswordReader("com.devbehindyou.atomicfilemanager")
        assertEquals("old secret", legacy.read(legacy.writeForTest("old secret")))
        assertEquals("plain", legacy.read("plain"))
    }
}
