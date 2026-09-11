package com.devbehindyou.refract.data.hide

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

object FastObscureHelper {

    private const val MAGIC_FOOTER = "REFRACT_OBSCURE_V1"
    private const val HEADER_REGION_SIZE = 512
    private const val XOR_KEY: Byte = 0x5A.toByte()

    const val OBSCURE_EXTENSION = ".refract_obscured"

    /**
     * Obscures a file in O(1) time by:
     * 1. Reading the first up to 512 signature bytes
     * 2. Overwriting the first bytes with an inverted mask so OS/apps cannot recognize it
     * 3. Appending a self-contained recovery footer containing the original filename and original bytes
     * 4. Renaming the file with [.refract_obscured] extension
     */
    fun obscureFile(file: File): Result<File> {
        if (!file.exists() || !file.isFile) {
            return Result.failure(IllegalArgumentException("Target must be an existing file"))
        }

        val originalName = file.name
        val originalLength = file.length()
        val regionSize = minOf(originalLength, HEADER_REGION_SIZE.toLong()).toInt()

        try {
            RandomAccessFile(file, "rw").use { raf ->
                // 1. Read original header bytes
                val originalHeader = ByteArray(regionSize)
                raf.seek(0)
                raf.readFully(originalHeader)

                // 2. Calculate CRC32 of original header
                val crc = CRC32()
                crc.update(originalHeader)
                val checksum = crc.value

                // 3. Mask original header bytes in-place
                val maskedHeader = ByteArray(regionSize) { i -> (originalHeader[i].toInt() xor XOR_KEY.toInt()).toByte() }
                raf.seek(0)
                raf.write(maskedHeader)

                // 4. Append recovery footer at the end of the file
                raf.seek(originalLength)

                // Write original header bytes (XOR masked for safety)
                raf.write(maskedHeader)

                // Write original file name
                val nameBytes = originalName.toByteArray(StandardCharsets.UTF_8)
                raf.write(nameBytes)
                raf.writeInt(nameBytes.size)

                // Write metadata: checksum, region size, original file size
                raf.writeLong(checksum)
                raf.writeInt(regionSize)
                raf.writeLong(originalLength)

                // Write magic footer marker
                val magicBytes = MAGIC_FOOTER.toByteArray(StandardCharsets.UTF_8)
                raf.write(magicBytes)
                raf.writeInt(magicBytes.size)
            }

            val targetFile = File(file.parentFile, "$originalName$OBSCURE_EXTENSION")
            val renamed = file.renameTo(targetFile)
            return if (renamed) {
                Result.success(targetFile)
            } else {
                // Rollback if rename fails
                restoreFile(file)
                Result.failure(IllegalStateException("Failed to rename file after obfuscation"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Reversibly restores an obscured file to its exact original state.
     */
    fun restoreFile(file: File): Result<File> {
        if (!file.exists() || !file.isFile) {
            return Result.failure(IllegalArgumentException("Target must be an existing file"))
        }

        try {
            var originalName: String
            var originalLength: Long

            RandomAccessFile(file, "rw").use { raf ->
                val fileLen = raf.length()
                if (fileLen < 32) {
                    return Result.failure(IllegalStateException("File too small to contain obscure footer"))
                }

                // 1. Read footer length and verify magic
                raf.seek(fileLen - 4)
                val footerLen = raf.readInt()
                if (footerLen <= 0 || footerLen > 64) {
                    return Result.failure(IllegalStateException("Invalid footer length"))
                }

                val footerBytes = ByteArray(footerLen)
                raf.seek(fileLen - 4 - footerLen)
                raf.readFully(footerBytes)
                val footerStr = String(footerBytes, StandardCharsets.UTF_8)
                if (footerStr != MAGIC_FOOTER) {
                    return Result.failure(IllegalStateException("File does not match RefractHiddenFormat v1"))
                }

                // 2. Read metadata fields right before footer
                var readOffset = fileLen - 4 - footerLen - 8 // originalLength (Long)
                raf.seek(readOffset)
                originalLength = raf.readLong()

                readOffset -= 4 // regionSize (Int)
                raf.seek(readOffset)
                val regionSize = raf.readInt()

                readOffset -= 8 // checksum (Long)
                raf.seek(readOffset)
                val expectedChecksum = raf.readLong()

                readOffset -= 4 // nameLen (Int)
                raf.seek(readOffset)
                val nameLen = raf.readInt()

                readOffset -= nameLen // originalName
                raf.seek(readOffset)
                val nameBytes = ByteArray(nameLen)
                raf.readFully(nameBytes)
                originalName = String(nameBytes, StandardCharsets.UTF_8)

                readOffset -= regionSize // masked original header
                raf.seek(readOffset)
                val maskedHeader = ByteArray(regionSize)
                raf.readFully(maskedHeader)

                // 3. Unmask and verify CRC32
                val restoredHeader = ByteArray(regionSize) { i -> (maskedHeader[i].toInt() xor XOR_KEY.toInt()).toByte() }
                val crc = CRC32()
                crc.update(restoredHeader)
                if (crc.value != expectedChecksum) {
                    return Result.failure(IllegalStateException("Checksum mismatch: file corruption detected"))
                }

                // 4. Restore original header in-place at offset 0
                raf.seek(0)
                raf.write(restoredHeader)

                // 5. Truncate file back to original length
                raf.setLength(originalLength)
                raf.fd.sync()
            }

            // 6. Rename back to original filename
            val targetFile = File(file.parentFile, originalName)
            val renamed = file.renameTo(targetFile)
            return if (renamed) {
                Result.success(targetFile)
            } else {
                Result.success(file)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
