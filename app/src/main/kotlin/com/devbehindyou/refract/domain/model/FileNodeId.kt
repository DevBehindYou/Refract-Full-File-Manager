package com.devbehindyou.refract.domain.model

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A stable, backend-qualified identity for a [FileNode] (`architecture/STORAGE_ARCHITECTURE.md`
 * §1). Opaque above the data layer — only backends parse it. Three prefixes, always
 * parseable, never guessed:
 * ```
 * file:/storage/emulated/0/Download/a.pdf
 * saf:content%3A%2F%2Fcom.android.externalstorage.documents%2Ftree%2F...
 * media:external:images:10432
 * ```
 *
 * The primary constructor is deliberately unvalidated (matching the bare `value class
 * FileNodeId(val raw: String)` shown in the architecture doc) — backends that already know
 * they're constructing a well-formed id can use it or the typed factories below directly.
 * Code parsing an *untrusted* string (e.g. a restored `SavedStateHandle` entry) should go
 * through [parse], which validates and rejects malformed input.
 */
@JvmInline
value class FileNodeId(val raw: String) {

    enum class Prefix(val scheme: String) {
        FILE("file:"),
        SAF("saf:"),
        MEDIA("media:"),
        USB("usb:"),
        SFTP("sftp:"),
        FTP("ftp:"),
        FTPS("ftps:"),
        SMB("smb:"),
        WEBDAV("webdav:"),
    }

    val prefix: Prefix?
        get() = Prefix.entries.firstOrNull { raw.startsWith(it.scheme) }

    companion object {
        private fun isAbsolutePath(path: String): Boolean {
            if (path.startsWith("/")) return true
            if (path.length >= 2 && path[1] == ':' && (path[0] in 'a'..'z' || path[0] in 'A'..'Z')) {
                return path.length == 2 || path[2] == '/' || path[2] == '\\'
            }
            return false
        }

        /** [path] must be absolute (start with `/` or Windows drive letter). */
        fun file(path: String): FileNodeId {
            require(isAbsolutePath(path)) { "file: path must be absolute, was \"$path\"" }
            return FileNodeId(Prefix.FILE.scheme + path)
        }

        fun saf(contentUri: String): FileNodeId {
            require(contentUri.isNotEmpty()) { "saf: content URI must not be empty" }
            return FileNodeId(Prefix.SAF.scheme + URLEncoder.encode(contentUri, "UTF-8"))
        }

        fun media(volume: String, collection: String, id: Long): FileNodeId {
            require(volume.isNotEmpty() && ':' !in volume) { "invalid media: volume \"$volume\"" }
            require(collection.isNotEmpty() && ':' !in collection) {
                "invalid media: collection \"$collection\""
            }
            return FileNodeId("${Prefix.MEDIA.scheme}$volume:$collection:$id")
        }

        fun usb(path: String): FileNodeId {
            require(path.isNotEmpty()) { "usb: path must not be empty" }
            return FileNodeId(Prefix.USB.scheme + path)
        }

        fun sftp(serverId: String, remotePath: String): FileNodeId {
            require(serverId.isNotEmpty()) { "sftp: serverId must not be empty" }
            return FileNodeId("${Prefix.SFTP.scheme}$serverId:$remotePath")
        }

        fun ftp(serverId: String, remotePath: String): FileNodeId {
            require(serverId.isNotEmpty()) { "ftp: serverId must not be empty" }
            return FileNodeId("${Prefix.FTP.scheme}$serverId:$remotePath")
        }

        fun ftps(serverId: String, remotePath: String): FileNodeId {
            require(serverId.isNotEmpty()) { "ftps: serverId must not be empty" }
            return FileNodeId("${Prefix.FTPS.scheme}$serverId:$remotePath")
        }

        fun smb(serverId: String, shareAndPath: String): FileNodeId {
            require(serverId.isNotEmpty()) { "smb: serverId must not be empty" }
            return FileNodeId("${Prefix.SMB.scheme}$serverId:$shareAndPath")
        }

        fun webdav(serverId: String, remotePath: String): FileNodeId {
            require(serverId.isNotEmpty()) { "webdav: serverId must not be empty" }
            return FileNodeId("${Prefix.WEBDAV.scheme}$serverId:$remotePath")
        }

        /**
         * Validates and parses [raw]. Returns `null` — never throws — on anything that
         * isn't one of the well-formed encodings above.
         */
        fun parse(raw: String): FileNodeId? {
            val prefix = Prefix.entries.firstOrNull { raw.startsWith(it.scheme) } ?: return null
            val body = raw.removePrefix(prefix.scheme)
            if (body.isEmpty()) return null

            val valid = when (prefix) {
                Prefix.FILE -> isAbsolutePath(body)
                Prefix.SAF -> runCatching { URLDecoder.decode(body, "UTF-8") }
                    .getOrNull()
                    ?.startsWith("content://") == true
                Prefix.MEDIA -> {
                    val parts = body.split(":")
                    parts.size == 3 &&
                        parts[0].isNotEmpty() &&
                        parts[1].isNotEmpty() &&
                        parts[2].toLongOrNull() != null
                }
                Prefix.USB, Prefix.SFTP, Prefix.FTP, Prefix.FTPS, Prefix.SMB, Prefix.WEBDAV -> {
                    body.isNotEmpty()
                }
            }
            return if (valid) FileNodeId(raw) else null
        }
    }
}
