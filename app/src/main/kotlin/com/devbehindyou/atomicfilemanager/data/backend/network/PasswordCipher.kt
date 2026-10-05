package com.devbehindyou.atomicfilemanager.data.backend.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Encrypts saved server passwords (ALL_IN_ONE_PLAN.md 0.4). */
interface PasswordCipher {
    /** The stored form of [plain]; throws if it cannot be encrypted (nothing is ever stored in plain text). */
    fun encrypt(plain: String): String

    /** The password, or null when [stored] is not this cipher's format or fails authentication. */
    fun decrypt(stored: String): String?

    /** True when [stored] was written by this cipher, so it needs no migration. */
    fun isCurrent(stored: String): Boolean
}

/**
 * AES-256-GCM with a fresh random IV per value. Stored as `v2:` + Base64(IV ‖ ciphertext ‖ tag).
 * GCM authenticates, so a changed or truncated value decrypts to null instead of garbage.
 * [key] is called for every operation; production passes the Android Keystore key.
 */
class AesGcmPasswordCipher(private val key: () -> SecretKey) : PasswordCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The provider picks the IV (Keystore keys refuse caller-chosen IVs by default).
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV length ${iv.size}" }
        val sealed = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + sealed)
    }

    override fun decrypt(stored: String): String? {
        if (!isCurrent(stored)) return null
        return try {
            val bytes = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            if (bytes.size <= IV_BYTES) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), StandardCharsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun isCurrent(stored: String): Boolean = stored.startsWith(PREFIX)

    companion object {
        const val PREFIX = "v2:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_ALIAS = "atomic_network_credentials_v2"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_BITS = 256

        /** The app's key in the Android Keystore, created on first use; it never leaves secure hardware where available. */
        fun androidKeystore(): AesGcmPasswordCipher = AesGcmPasswordCipher(::keystoreKey)

        @Synchronized
        private fun keystoreKey(): SecretKey {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}

/**
 * Reads values written before 0.4: AES-CBC with a key derived from the package name and a fixed
 * IV, or plain text where that failed. Only used to migrate them to [AesGcmPasswordCipher].
 */
internal class LegacyPasswordReader(packageName: String) {
    private val key =
        SecretKeySpec(
            MessageDigest
                .getInstance("SHA-256")
                .digest("$packageName.refract.credentials.v1".toByteArray(StandardCharsets.UTF_8)),
            "AES",
        )
    private val iv = IvParameterSpec(ByteArray(LEGACY_IV_BYTES) { (it * LEGACY_IV_STEP).toByte() })

    /** The old value's password: decrypted if it was encrypted, else the stored text itself. */
    fun read(stored: String): String =
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            String(cipher.doFinal(Base64.getDecoder().decode(stored)), StandardCharsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            stored
        } catch (_: IllegalArgumentException) {
            stored
        }

    /** Writes the old format; tests use it to prove migration. */
    fun writeForTest(plain: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)))
    }

    private companion object {
        const val LEGACY_IV_BYTES = 16
        const val LEGACY_IV_STEP = 7
    }
}
