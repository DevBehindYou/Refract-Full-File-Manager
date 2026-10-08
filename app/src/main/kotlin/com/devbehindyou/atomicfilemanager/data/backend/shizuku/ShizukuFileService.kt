package com.devbehindyou.atomicfilemanager.data.backend.shizuku

import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.system.exitProcess

/**
 * Runs in a separate process started by Shizuku as the shell user (ALL_IN_ONE_PLAN.md 4.3), which
 * can read `Android/data` and `Android/obb`. It only reads, and refuses any path outside those two
 * folders, so turning the Labs switch on never widens what the app can change.
 */
class ShizukuFileService : IShizukuFileService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun list(path: String): String {
        val dir = checked(path)
        val children = dir.listFiles() ?: error("Can't list $path")
        return ShizukuEntries.encode(children.map(::entryOf))
    }

    override fun stat(path: String): String {
        val file = checked(path)
        return if (file.exists()) ShizukuEntries.encode(listOf(entryOf(file))) else ""
    }

    override fun openRead(path: String): ParcelFileDescriptor {
        val file = checked(path)
        require(file.isFile) { "Not a file: $path" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun checked(path: String): File {
        val file = File(path)
        // The canonical path catches links that lead out of the two folders.
        val real = runCatching { file.canonicalPath }.getOrDefault(path)
        if (!ShizukuEntries.allowed(path) || !ShizukuEntries.allowed(real)) {
            throw SecurityException("Outside Android/data and Android/obb: $path")
        }
        return file
    }

    private fun entryOf(file: File): ShizukuEntry =
        ShizukuEntry(
            name = file.name,
            isDirectory = file.isDirectory,
            size = if (file.isDirectory) -1L else file.length(),
            modifiedAt = file.lastModified(),
        )
}
