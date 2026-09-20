package com.devbehindyou.refract.data.repository

import android.content.Context
import com.devbehindyou.refract.data.database.HiddenFilesDatabaseHelper
import com.devbehindyou.refract.data.hide.FastObscureHelper
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.HiddenItem
import com.devbehindyou.refract.domain.model.HideJournalEntry
import com.devbehindyou.refract.domain.model.HideMode
import com.devbehindyou.refract.domain.model.JournalState
import com.devbehindyou.refract.domain.repository.HiddenFilesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

class HiddenFilesRepositoryImpl(
    private val context: Context,
    private val dbHelper: HiddenFilesDatabaseHelper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Folder (relative to a storage root) for hidden gallery files; `null` keeps them next to the original. */
    private val hiddenFolderProvider: () -> String? = { null },
    private val volumeRootResolver: (File) -> File? = ::volumeRootOf,
) : HiddenFilesRepository {
    private val _hiddenItems = MutableStateFlow<List<HiddenItem>>(emptyList())
    override val hiddenItems: StateFlow<List<HiddenItem>> = _hiddenItems.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        _hiddenItems.value = dbHelper.getAllHiddenItems()
    }

    /**
     * Runs [block] on the IO dispatcher. Any exception (for example an IOException from
     * `createNewFile` when the original folder is gone) becomes a failed [Result] instead of
     * escaping into the UI coroutine, where nothing catches it and the app crashes.
     */
    private suspend fun <T> io(block: suspend () -> Result<T>): Result<T> =
        withContext(ioDispatcher) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun hideFromGallery(node: FileNode): Result<HiddenItem> =
        io {
            val sourceFile =
                node.id.localFileOrNull()
                    ?: return@io Result.failure(IllegalArgumentException("Hiding requires a local file"))
            if (!sourceFile.exists()) {
                return@io Result.failure(IllegalArgumentException("Source file does not exist"))
            }

            val parentDir =
                sourceFile.parentFile
                    ?: return@io Result.failure(IllegalStateException("No parent directory"))
            // Central folder on the same storage (default Refract/Hidden); otherwise beside the file.
            val centralDir =
                hiddenFolderProvider()?.let { relative -> volumeRootResolver(sourceFile)?.let { File(it, relative) } }
            val hiddenDir = centralDir ?: File(parentDir, ".RefractHidden")
            if (parentDir.absoluteFile == hiddenDir.absoluteFile) {
                return@io Result.failure(IllegalStateException("File is already in the hidden folder"))
            }
            if (!hiddenDir.exists()) {
                hiddenDir.mkdirs()
            }
            if (!hiddenDir.isDirectory) {
                return@io Result.failure(IllegalStateException("Could not create ${hiddenDir.absolutePath}"))
            }

            // Ensure .nomedia exists in the hidden directory
            val noMedia = File(hiddenDir, ".nomedia")
            if (!noMedia.exists()) {
                noMedia.createNewFile()
            }

            // Files from different folders share the central folder, so a name clash gets a suffix.
            val targetFile =
                if (centralDir != null) uniqueChild(hiddenDir, sourceFile.name) else File(hiddenDir, sourceFile.name)
            if (targetFile.exists()) {
                return@io Result.failure(
                    IllegalStateException("Hidden filename already exists"),
                )
            }
            val moved = sourceFile.renameTo(targetFile)
            if (!moved) {
                return@io Result.failure(
                    IllegalStateException("Failed to move file to hidden gallery directory"),
                )
            }

            val item =
                HiddenItem(
                    id = UUID.randomUUID().toString(),
                    originalLocation = sourceFile.absolutePath,
                    currentLocation = targetFile.absolutePath,
                    originalName = sourceFile.name,
                    size = targetFile.length(),
                    mode = HideMode.GALLERY,
                )

            dbHelper.insertHiddenItem(item)
            refresh()
            Result.success(item)
        }

    override suspend fun unhideFromGallery(item: HiddenItem): Result<Unit> =
        io {
            val currentFile = File(item.currentLocation)
            val originalFile = File(item.originalLocation)
            if (!currentFile.exists()) {
                return@io Result.failure(
                    IllegalStateException("Hidden file is unavailable"),
                )
            }
            if (originalFile.exists()) {
                return@io Result.failure(
                    IllegalStateException("Original filename already exists"),
                )
            }

            if (currentFile.exists()) {
                // The original folder may have been deleted since the file was hidden.
                originalFile.parentFile?.mkdirs()
                val moved = currentFile.renameTo(originalFile)
                if (!moved) {
                    return@io Result.failure(
                        IllegalStateException("Failed to restore file from hidden directory"),
                    )
                }
            }

            dbHelper.deleteHiddenItem(item.id)
            refresh()
            Result.success(Unit)
        }

    override suspend fun fastObscure(node: FileNode): Result<HiddenItem> =
        io {
            val sourceFile =
                node.id.localFileOrNull()
                    ?: return@io Result.failure(IllegalArgumentException("Obscuring requires a local file"))
            val opId = UUID.randomUUID().toString()

            dbHelper.logJournal(
                HideJournalEntry(
                    operationId = opId,
                    originalPath = sourceFile.absolutePath,
                    currentPath = sourceFile.absolutePath,
                    originalName = sourceFile.name,
                    state = JournalState.PREPARING,
                    mode = HideMode.FAST_OBSCURE,
                ),
            )

            val obscureResult = FastObscureHelper.obscureFile(sourceFile)
            val targetFile =
                obscureResult.getOrElse {
                    dbHelper.logJournal(
                        HideJournalEntry(
                            operationId = opId,
                            originalPath = sourceFile.absolutePath,
                            currentPath = sourceFile.absolutePath,
                            originalName = sourceFile.name,
                            state = JournalState.ROLLBACK_REQUIRED,
                            mode = HideMode.FAST_OBSCURE,
                        ),
                    )
                    return@io Result.failure(it)
                }

            dbHelper.logJournal(
                HideJournalEntry(
                    operationId = opId,
                    originalPath = sourceFile.absolutePath,
                    currentPath = targetFile.absolutePath,
                    originalName = sourceFile.name,
                    state = JournalState.COMMITTED,
                    mode = HideMode.FAST_OBSCURE,
                ),
            )

            val item =
                HiddenItem(
                    id = opId,
                    originalLocation = sourceFile.absolutePath,
                    currentLocation = targetFile.absolutePath,
                    originalName = sourceFile.name,
                    size = targetFile.length(),
                    mode = HideMode.FAST_OBSCURE,
                )

            dbHelper.insertHiddenItem(item)
            refresh()
            Result.success(item)
        }

    override suspend fun restoreFastObscured(item: HiddenItem): Result<Unit> =
        io {
            val currentFile = File(item.currentLocation)
            val restoreResult = FastObscureHelper.restoreFile(currentFile)
            if (restoreResult.isFailure) {
                return@io Result.failure(restoreResult.exceptionOrNull()!!)
            }

            dbHelper.deleteHiddenItem(item.id)
            dbHelper.clearJournal(item.id)
            refresh()
            Result.success(Unit)
        }

    override suspend fun moveToPrivateStorage(node: FileNode): Result<HiddenItem> =
        io {
            val sourceFile =
                node.id.localFileOrNull()

                    ?: return@io Result.failure(
                        IllegalArgumentException("Private storage requires a local file"),
                    )
            if (!sourceFile.exists()) {
                return@io Result.failure(IllegalArgumentException("Source file does not exist"))
            }

            val privateDir = File(context.filesDir, "private_storage")
            if (!privateDir.exists()) {
                privateDir.mkdirs()
            }

            val targetFile = File(privateDir, sourceFile.name)
            if (!targetFile.createNewFile()) {
                return@io Result.failure(
                    IllegalStateException("Private filename already exists"),
                )
            }

            try {
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }

                if (targetFile.length() != sourceFile.length()) {
                    targetFile.delete()
                    return@io Result.failure(
                        IllegalStateException("Size verification failed after copy to private storage"),
                    )
                }

                // Only delete source after verified
                if (!sourceFile.delete()) {
                    targetFile.delete()
                    return@io Result.failure(IllegalStateException("Could not remove original file"))
                }

                val item =
                    HiddenItem(
                        id = UUID.randomUUID().toString(),
                        originalLocation = sourceFile.absolutePath,
                        currentLocation = targetFile.absolutePath,
                        originalName = sourceFile.name,
                        size = targetFile.length(),
                        mode = HideMode.PRIVATE_STORAGE,
                    )

                dbHelper.insertHiddenItem(item)
                refresh()
                Result.success(item)
            } catch (e: Exception) {
                if (sourceFile.exists() && targetFile.exists()) targetFile.delete()
                if (e is CancellationException) throw e
                Result.failure(e)
            }
        }

    override suspend fun restoreFromPrivateStorage(
        item: HiddenItem,
        destinationParent: FileNodeId,
    ): Result<Unit> =
        io {
            val privateFile = File(item.currentLocation)
            if (!privateFile.exists()) {
                return@io Result.failure(IllegalStateException("Private file no longer exists"))
            }

            val destDir =
                destinationParent.localFileOrNull()

                    ?: return@io Result.failure(
                        IllegalArgumentException("Restoration requires a local folder"),
                    )
            // The original folder may have been deleted or renamed since the file was hidden.
            destDir.mkdirs()
            val targetFile = File(destDir, item.originalName)
            if (!targetFile.createNewFile()) {
                return@io Result.failure(
                    IllegalStateException("Destination filename already exists"),
                )
            }

            try {
                FileInputStream(privateFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }

                if (targetFile.length() == privateFile.length()) {
                    if (!privateFile.delete()) {
                        return@io Result.failure(
                            IllegalStateException("Could not remove private original"),
                        )
                    }
                    dbHelper.deleteHiddenItem(item.id)
                    refresh()
                    Result.success(Unit)
                } else {
                    targetFile.delete()
                    Result.failure(IllegalStateException("Restoration size mismatch"))
                }
            } catch (e: Exception) {
                if (privateFile.exists()) targetFile.delete()
                if (e is CancellationException) throw e
                Result.failure(e)
            }
        }

    override suspend fun deleteHiddenItem(item: HiddenItem): Result<Unit> =
        io {
            val file = File(item.currentLocation)
            if (file.exists()) {
                if (!file.delete()) {
                    return@io Result.failure(
                        IllegalStateException("Could not delete hidden file"),
                    )
                }
            }
            dbHelper.deleteHiddenItem(item.id)
            refresh()
            Result.success(Unit)
        }

    override suspend fun recoverUnfinishedOperations() =
        withContext(ioDispatcher) {
            val unfinished = dbHelper.getUnfinishedJournalEntries()
            for (entry in unfinished) {
                when (entry.state) {
                    JournalState.PREPARING,
                    JournalState.HEADER_TRANSFORMED,
                    JournalState.ROLLBACK_REQUIRED,
                    -> {
                        val targetFile =
                            listOf(
                                File(entry.currentPath),
                                File(entry.originalPath + FastObscureHelper.OBSCURE_EXTENSION),
                            ).firstOrNull { it.exists() }
                        if (targetFile != null && FastObscureHelper.restoreFile(targetFile).isSuccess) {
                            dbHelper.clearJournal(entry.operationId)
                        }
                    }
                    JournalState.RENAMED -> {
                        // Commit into hidden_items
                        val currentFile = File(entry.currentPath)
                        if (currentFile.exists()) {
                            dbHelper.insertHiddenItem(
                                HiddenItem(
                                    id = entry.operationId,
                                    originalLocation = entry.originalPath,
                                    currentLocation = entry.currentPath,
                                    originalName = entry.originalName,
                                    size = currentFile.length(),
                                    mode = entry.mode,
                                ),
                            )
                        }
                        dbHelper.clearJournal(entry.operationId)
                    }
                    JournalState.COMMITTED, JournalState.RESTORED -> {
                        dbHelper.clearJournal(entry.operationId)
                    }
                }
            }
            refresh()
        }

    private fun FileNodeId.localFileOrNull(): File? =
        if (prefix == FileNodeId.Prefix.FILE) File(raw.removePrefix(FileNodeId.Prefix.FILE.scheme)) else null
}

/** `/storage/emulated/0/...` gives `/storage/emulated/0`, `/storage/ABCD-1234/...` gives `/storage/ABCD-1234`. */
internal fun volumeRootOf(file: File): File? {
    val segments = file.absolutePath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
    return when {
        segments.size < 2 || segments[0] != "storage" -> null
        segments[1] == "emulated" -> if (segments.size >= 3) File("/storage/emulated/${segments[2]}") else null
        segments[1] == "self" -> null
        else -> File("/storage/${segments[1]}")
    }
}

/** [name] inside [dir], or `name (1).ext`, `name (2).ext`... when that name is taken. */
internal fun uniqueChild(
    dir: File,
    name: String,
): File {
    val plain = File(dir, name)
    if (!plain.exists()) return plain
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""
    for (number in 1..MAX_NAME_SUFFIX) {
        val candidate = File(dir, "$stem ($number)$extension")
        if (!candidate.exists()) return candidate
    }
    return plain
}

private const val MAX_NAME_SUFFIX = 999
