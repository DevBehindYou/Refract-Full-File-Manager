package com.devbehindyou.atomicfilemanager.data.vault

import com.google.crypto.tink.StreamingAead
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Encrypts files into the vault and back out (ALL_IN_ONE_PLAN.md 2.5) with Tink StreamingAead
 * (AES-256-GCM-HKDF, 1 MB segments), so large files never sit in memory and reads can seek.
 *
 * Encryption never touches the original: the ciphertext goes to a temporary file next to the
 * target, is read back and decrypted to check its SHA-256 matches what was read from the source,
 * and only then takes the target name. Removing the original is the caller's next step, after this
 * returns success. [associatedData] (for example the vault item id) binds a ciphertext to its record,
 * so two vault files can't be swapped on disk unnoticed.
 */
class VaultFiles(
    private val aead: () -> StreamingAead,
) {
    /** Size of the encrypted file on success. */
    fun encrypt(
        source: InputStream,
        target: File,
        associatedData: ByteArray,
    ): Result<Long> {
        val temp = File(target.parentFile, ".${target.name}.part")
        return runCatching {
            target.parentFile?.mkdirs()
            val primitive = aead()
            val plainDigest = MessageDigest.getInstance(SHA_256)
            // Closing the encrypting stream writes the last segment and closes the file, which syncs first.
            primitive.newEncryptingStream(SyncOnClose(FileOutputStream(temp)), associatedData).use { encrypting ->
                source.copyHashing(encrypting, plainDigest)
            }
            val checkDigest = MessageDigest.getInstance(SHA_256)
            temp.inputStream().use { raw ->
                primitive.newDecryptingStream(raw, associatedData).use { decrypting ->
                    decrypting.copyHashing(NullOutput, checkDigest)
                }
            }
            if (!MessageDigest.isEqual(plainDigest.digest(), checkDigest.digest())) {
                throw IOException("Vault copy didn't read back the same; the original is untouched")
            }
            if (target.exists() || !temp.renameTo(target)) {
                throw IOException("Couldn't place the vault file ${target.name}")
            }
            target.length()
        }.onFailure { temp.delete() }
    }

    /** Writes the plain content of [source] to [output]; fails on a wrong key, tampering or truncation. */
    fun decrypt(
        source: File,
        output: OutputStream,
        associatedData: ByteArray,
    ): Result<Long> =
        runCatching {
            var total = 0L
            source.inputStream().use { raw ->
                aead().newDecryptingStream(raw, associatedData).use { decrypting ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = decrypting.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        total += read
                    }
                }
            }
            total
        }

    private fun InputStream.copyHashing(
        output: OutputStream,
        digest: MessageDigest,
    ) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
    }

    private class SyncOnClose(
        private val file: FileOutputStream,
    ) : FilterOutputStream(file) {
        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) = file.write(b, off, len)

        override fun close() {
            file.flush()
            file.fd.sync()
            file.close()
        }
    }

    private object NullOutput : OutputStream() {
        override fun write(b: Int) = Unit

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) = Unit
    }

    private companion object {
        const val SHA_256 = "SHA-256"
        const val BUFFER = 64 * 1024
    }
}
