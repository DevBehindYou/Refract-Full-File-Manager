package com.devbehindyou.atomicfilemanager.domain.usecase

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.util.zip.ZipInputStream

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
 * is extracted to look inside. ZIP keeps the platform reader; TAR and the single-file compressors
 * (GZ, BZ2, XZ) come from Apache Commons Compress. A lone compressed file lists as one entry.
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

    fun open(
        kind: Kind,
        name: String,
        input: InputStream,
        size: Long = -1,
    ): ArchiveStream {
        val buffered = BufferedInputStream(input)
        return when (kind) {
            Kind.ZIP -> ZipStream(ZipInputStream(buffered))
            Kind.TAR -> CommonsStream(TarArchiveInputStream(buffered))
            Kind.TAR_GZ -> CommonsStream(TarArchiveInputStream(GzipCompressorInputStream(buffered, true)))
            Kind.TAR_BZ2 -> CommonsStream(TarArchiveInputStream(BZip2CompressorInputStream(buffered, true)))
            Kind.TAR_XZ -> CommonsStream(TarArchiveInputStream(XZCompressorInputStream(buffered, true)))
            Kind.GZ -> SingleStream(GzipCompressorInputStream(buffered, true), innerName(name, kind), size)
            Kind.BZ2 -> SingleStream(BZip2CompressorInputStream(buffered, true), innerName(name, kind), size)
            Kind.XZ -> SingleStream(XZCompressorInputStream(buffered, true), innerName(name, kind), size)
        }
    }

    private class ZipStream(private val zip: ZipInputStream) : ArchiveStream {
        override fun next(): ArchiveHeader? =
            zip.nextEntry?.let {
                ArchiveHeader(it.name, it.isDirectory, it.size, it.compressedSize, it.time.coerceAtLeast(0L))
            }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = zip.read(buffer, offset, length)

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

/** True when [path], cleaned of leading slashes and backslashes, would climb out of the target folder. */
internal fun escapesTarget(path: String): Boolean = path.replace('\\', '/').trimStart('/').split('/').any { it == ".." }
