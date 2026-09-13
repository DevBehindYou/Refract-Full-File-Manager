package com.devbehindyou.refract.data.hide

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.CRC32

/** Reversible header obfuscation. This format is not encryption or authentication. */
object FastObscureHelper {
    private const val MAGIC_FOOTER = "REFRACT_OBSCURE_V1"
    private const val HEADER_REGION_SIZE = 512
    private const val XOR_KEY = 0x5A
    const val OBSCURE_EXTENSION = ".refract_obscured"

    fun obscureFile(file: File): Result<File> =
        runCatching {
            require(file.isFile) { "Target must be an existing file" }
            require(!file.name.endsWith(OBSCURE_EXTENSION)) { "File is already obscured" }
            val target = File(file.parentFile, file.name + OBSCURE_EXTENSION)
            require(!target.exists()) { "Obscured filename already exists" }
            val originalLength = file.length()
            var originalHeader: ByteArray? = null
            try {
                RandomAccessFile(file, "rw").use { raf ->
                    val header = ByteArray(minOf(originalLength, HEADER_REGION_SIZE.toLong()).toInt())
                    raf.readFully(header)
                    originalHeader = header
                    val masked = ByteArray(header.size) { (header[it].toInt() xor XOR_KEY).toByte() }
                    val checksum = CRC32().apply { update(header) }.value
                    // Persist complete recovery metadata BEFORE changing any original byte.
                    raf.seek(originalLength)
                    raf.write(masked)
                    val nameBytes = file.name.toByteArray(StandardCharsets.UTF_8)
                    raf.write(nameBytes)
                    raf.writeInt(nameBytes.size)
                    raf.writeLong(checksum)
                    raf.writeInt(header.size)
                    raf.writeLong(originalLength)
                    val magic = MAGIC_FOOTER.toByteArray(StandardCharsets.UTF_8)
                    raf.write(magic)
                    raf.writeInt(magic.size)
                    raf.fd.sync()
                    raf.seek(0)
                    raf.write(masked)
                    raf.fd.sync()
                }
                Files.move(file.toPath(), target.toPath())
                target
            } catch (error: Exception) {
                // If interrupted before the rename, restore the bounded header and remove only
                // our footer. A failed rollback leaves the error visible to journal recovery.
                if (file.exists() && originalHeader != null) {
                    try {
                        RandomAccessFile(file, "rw").use { raf ->
                            raf.seek(0)
                            raf.write(originalHeader!!)
                            raf.setLength(originalLength)
                            raf.fd.sync()
                        }
                    } catch (rollback: Exception) {
                        error.addSuppressed(rollback)
                    }
                }
                throw error
            }
        }

    fun restoreFile(file: File): Result<File> =
        runCatching {
            require(file.isFile) { "Target must be an existing file" }
            val metadata = readRecovery(file)
            val target = File(file.parentFile, metadata.name)
            require(target.canonicalFile.parentFile == file.canonicalFile.parentFile) { "Invalid recovery filename" }
            if (target.absolutePath != file.absolutePath) {
                require(!target.exists()) { "Original filename already exists" }
                // Fail on collisions before touching the recoverable header/footer.
                Files.move(file.toPath(), target.toPath())
            }
            RandomAccessFile(target, "rw").use { raf ->
                raf.seek(0)
                raf.write(metadata.header)
                raf.fd.sync()
                raf.setLength(metadata.length)
                raf.fd.sync()
            }
            target
        }

    private data class Recovery(val name: String, val length: Long, val header: ByteArray)

    private fun readRecovery(file: File): Recovery =
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val magic = MAGIC_FOOTER.toByteArray(StandardCharsets.UTF_8)
            require(length >= magic.size + 4 + 24) { "File too small for recovery metadata" }
            raf.seek(length - 4)
            require(raf.readInt() == magic.size) { "Invalid footer length" }
            raf.seek(length - 4 - magic.size)
            val actualMagic = ByteArray(magic.size)
            raf.readFully(actualMagic)
            require(magic.contentEquals(actualMagic)) { "Invalid RefractHiddenFormat marker" }
            val metadataOffset = length - 4 - magic.size - 24
            raf.seek(metadataOffset)
            val nameLength = raf.readInt()
            val checksum = raf.readLong()
            val regionSize = raf.readInt()
            val originalLength = raf.readLong()
            require(nameLength in 1..4096) { "Invalid recovery filename length" }
            require(originalLength >= 0 && originalLength <= metadataOffset) { "Invalid original length" }
            require(
                regionSize == minOf(originalLength, HEADER_REGION_SIZE.toLong()).toInt(),
            ) { "Invalid header region" }
            require(metadataOffset - nameLength - regionSize == originalLength) { "Inconsistent recovery offsets" }
            raf.seek(originalLength)
            val masked = ByteArray(regionSize)
            raf.readFully(masked)
            val nameBytes = ByteArray(nameLength)
            raf.readFully(nameBytes)
            val name = String(nameBytes, StandardCharsets.UTF_8)
            require(name.isNotBlank() && name != "." && name != "..") { "Invalid filename" }
            require(name.none { it == '/' || it == '\\' || it == ':' || it == '\u0000' }) { "Unsafe recovery filename" }
            val header = ByteArray(regionSize) { (masked[it].toInt() xor XOR_KEY).toByte() }
            require(CRC32().apply { update(header) }.value == checksum) { "Recovery header checksum mismatch" }
            Recovery(name, originalLength, header)
        }
}
