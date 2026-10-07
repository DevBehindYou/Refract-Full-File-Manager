package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.github.junrar.Archive
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import net.lingala.zip4j.exception.ZipException as Zip4jException
import net.lingala.zip4j.io.inputstream.ZipInputStream as Zip4jInputStream

/** One entry as an archive lists it; [path] is as stored, before any safety check. */
data class ArchiveHeader(
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val modifiedAt: Long,
)

/** Reads an archive entry by entry; [read] reads the current entry's bytes. */
interface ArchiveStream : Closeable {
    fun next(): ArchiveHeader?

    fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int
}

/**
 * Archive formats the app opens (ALL_IN_ONE_PLAN.md 2.2). Each kind is read as a stream, so nothing
 * is extracted to look inside. ZIP keeps the platform reader; TAR, 7z and the single-file compressors
 * (GZ, BZ2, XZ) come from Apache Commons Compress, RAR from junrar (read only: its licence forbids
 * writing RAR). A lone compressed file lists as one entry. 7z needs random access, so it is first
 * copied to a temporary file that is deleted when the stream closes.
 */
object ArchiveFormats {
    enum class Kind(vararg val suffixes: String) {
        ZIP(".zip"),
        TAR(".tar"),
        TAR_GZ(".tar.gz", ".tgz"),
        TAR_BZ2(".tar.bz2", ".tbz2", ".tbz"),
        TAR_XZ(".tar.xz", ".txz"),
        GZ(".gz"),
        BZ2(".bz2"),
        XZ(".xz"),
        SEVEN_Z(".7z"),
        RAR(".rar"),
    }

    /** At most this many entries are read: a guard against archives built to exhaust memory. */
    const val MAX_ENTRIES = 100_000

    /** The kind for a file name, longest suffix first so "a.tar.gz" is TAR_GZ, not GZ. */
    fun kindOf(name: String): Kind? {
        val lower = name.lowercase()
        return Kind.entries
            .flatMap { kind -> kind.suffixes.map { it to kind } }
            .sortedByDescending { it.first.length }
            .firstOrNull { (suffix, _) -> lower.endsWith(suffix) && lower.length > suffix.length }
            ?.second
    }

    /** The name a lone compressed file extracts to: "notes.txt.gz" gives "notes.txt". */
    fun innerName(
        name: String,
        kind: Kind,
    ): String {
        val suffix = kind.suffixes.firstOrNull { name.lowercase().endsWith(it) } ?: return name
        return name.dropLast(suffix.length).ifEmpty { "file" }
    }

    /** [password] opens encrypted ZIPs (ZipCrypto or AES) through zip4j; other kinds ignore it. */
    fun open(
        kind: Kind,
        name: String,
        input: InputStream,
        size: Long = -1,
        password: CharArray? = null,
    ): ArchiveStream {
        val buffered = BufferedInputStream(input)
        return when (kind) {
            Kind.ZIP ->
                if (password != null) {
                    Zip4jStream(Zip4jInputStream(buffered, password))
                } else {
                    ZipStream(ZipInputStream(buffered))
                }
            Kind.TAR -> CommonsStream(TarArchiveInputStream(buffered))
            Kind.TAR_GZ -> CommonsStream(TarArchiveInputStream(GzipCompressorInputStream(buffered, true)))
            Kind.TAR_BZ2 -> CommonsStream(TarArchiveInputStream(BZip2CompressorInputStream(buffered, true)))
            Kind.TAR_XZ -> CommonsStream(TarArchiveInputStream(XZCompressorInputStream(buffered, true)))
            Kind.GZ -> SingleStream(GzipCompressorInputStream(buffered, true), innerName(name, kind), size)
            Kind.BZ2 -> SingleStream(BZip2CompressorInputStream(buffered, true), innerName(name, kind), size)
            Kind.XZ -> SingleStream(XZCompressorInputStream(buffered, true), innerName(name, kind), size)
            Kind.SEVEN_Z -> SevenZStream.open(buffered)
            Kind.RAR -> RarStream(Archive(buffered))
        }
    }

    private class ZipStream(private val zip: ZipInputStream) : ArchiveStream {
        override fun next(): ArchiveHeader? =
            passwordAware { zip.nextEntry }?.let {
                ArchiveHeader(it.name, it.isDirectory, it.size, it.compressedSize, it.time.coerceAtLeast(0L))
            }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = passwordAware { zip.read(buffer, offset, length) }

        /** The platform reader can't decrypt; say so instead of calling the archive damaged. */
        private inline fun <T> passwordAware(block: () -> T): T =
            try {
                block()
            } catch (e: ZipException) {
                if (e.message.orEmpty().contains("encrypted", ignoreCase = true)) throw ArchivePasswordRequired()
                throw e
            }

        override fun close() = zip.close()
    }

    private class CommonsStream(private val archive: ArchiveInputStream<*>) : ArchiveStream {
        override fun next(): ArchiveHeader? {
            while (true) {
                val entry = archive.nextEntry ?: return null
                // Skip entries this reader can't extract (for example a sparse file in an unusual layout).
                if (!archive.canReadEntryData(entry)) continue
                return ArchiveHeader(
                    path = entry.name,
                    isDirectory = entry.isDirectory,
                    size = entry.size,
                    compressedSize = -1,
                    modifiedAt = entry.lastModifiedDate?.time ?: 0L,
                )
            }
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = archive.read(buffer, offset, length)

        override fun close() = archive.close()
    }

    private class SingleStream(
        private val input: InputStream,
        private val name: String,
        private val compressedSize: Long,
    ) : ArchiveStream {
        private var listed = false

        override fun next(): ArchiveHeader? {
            if (listed) return null
            listed = true
            return ArchiveHeader(name, isDirectory = false, size = -1, compressedSize = compressedSize, modifiedAt = 0L)
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = input.read(buffer, offset, length)

        override fun close() = input.close()
    }
}

/** Shown as the "format" of an archive opened without its password. */
const val PASSWORD_PROTECTED = "password-protected archive"

/** Shown as the "format" when the password given for an archive is wrong. */
const val WRONG_PASSWORD = "wrong password"

/** The password given for an archive doesn't open it. */
class ArchiveWrongPassword : IOException("Wrong password for this archive")

/**
 * Passwords for archives about to be extracted, kept in memory only and used once
 * (ALL_IN_ONE_PLAN.md 2.2: a password is never stored). After the app is closed the extract
 * fails as password-protected and the preview asks again.
 */
object ArchivePasswords {
    private val byArchive = ConcurrentHashMap<String, CharArray>()

    fun put(
        archive: FileNodeId,
        password: CharArray,
    ) {
        byArchive[archive.raw] = password.copyOf()
    }

    /** The password for [archive], removed as it is read. */
    fun take(archive: FileNodeId): CharArray? = byArchive.remove(archive.raw)
}

private class Zip4jStream(private val zip: Zip4jInputStream) : ArchiveStream {
    override fun next(): ArchiveHeader? {
        val header = zip4j { zip.nextEntry } ?: return null
        return ArchiveHeader(
            path = header.fileName,
            isDirectory = header.isDirectory,
            size = header.uncompressedSize,
            compressedSize = header.compressedSize,
            modifiedAt = header.lastModifiedTimeEpoch,
        )
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = zip4j { zip.read(buffer, offset, length) }

    override fun close() = zip.close()

    private inline fun <T> zip4j(block: () -> T): T =
        try {
            block()
        } catch (e: Zip4jException) {
            if (e.type == Zip4jException.Type.WRONG_PASSWORD) throw ArchiveWrongPassword()
            throw e
        }
}

/** The archive (or one of its entries) is password-protected, which the app can't open yet. */
class ArchivePasswordRequired : IOException("This archive is password-protected")

private class SevenZStream(
    private val file: SevenZFile,
    private val temp: File,
) : ArchiveStream {
    override fun next(): ArchiveHeader? {
        val entry = file.nextEntry ?: return null
        return ArchiveHeader(
            path = entry.name,
            isDirectory = entry.isDirectory,
            size = entry.size,
            compressedSize = -1,
            modifiedAt = if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else 0L,
        )
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = file.read(buffer, offset, length)

    override fun close() {
        try {
            file.close()
        } finally {
            temp.delete()
        }
    }

    companion object {
        fun open(input: InputStream): SevenZStream {
            val temp = File.createTempFile("atomic-7z-", ".7z")
            try {
                temp.outputStream().use { input.copyTo(it) }
                return SevenZStream(SevenZFile.builder().setFile(temp).get(), temp)
            } catch (e: IOException) {
                temp.delete()
                throw e
            }
        }
    }
}

private class RarStream(private val archive: Archive) : ArchiveStream {
    private var current: InputStream? = null

    override fun next(): ArchiveHeader? {
        current?.close()
        current = null
        val header = archive.nextFileHeader() ?: return null
        if (header.isEncrypted || archive.isEncrypted) throw ArchivePasswordRequired()
        if (!header.isDirectory) current = archive.getInputStream(header)
        return ArchiveHeader(
            path = header.fileName,
            isDirectory = header.isDirectory,
            size = header.fullUnpackSize,
            compressedSize = header.fullPackSize,
            modifiedAt = header.mTime?.time ?: 0L,
        )
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = current?.read(buffer, offset, length) ?: -1

    override fun close() {
        try {
            current?.close()
        } finally {
            archive.close()
        }
    }
}

/** True when [path], cleaned of leading slashes and backslashes, would climb out of the target folder. */
internal fun escapesTarget(path: String): Boolean = path.replace('\\', '/').trimStart('/').split('/').any { it == ".." }
