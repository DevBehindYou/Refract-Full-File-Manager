package com.devbehindyou.refract.data.backend

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.devbehindyou.refract.data.mapping.ErrorContext
import com.devbehindyou.refract.data.mapping.toFileError
import com.devbehindyou.refract.domain.model.AccessFlags
import com.devbehindyou.refract.domain.model.AccessTarget
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.StorageType
import com.devbehindyou.refract.domain.repository.BackendType
import com.devbehindyou.refract.domain.repository.InputStreamProvider
import com.devbehindyou.refract.domain.repository.OutputTarget
import com.devbehindyou.refract.domain.repository.StorageBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

/**
 * `MediaStore` — projection/selection/sortOrder queries, `IS_PENDING` writes,
 * `RecoverableSecurityException` handling for deletes (`architecture/DATA_LAYER.md` §5).
 *
 * **A real architectural finding, not an implementation detail:** `MediaStore` is a flat,
 * query-based index over media files — it has no directory-creation operation and no
 * portable "list this folder's contents" query in the filesystem sense. `StorageBackend`
 * was designed as one hierarchical interface for three backends
 * (`architecture/STORAGE_ARCHITECTURE.md`), and this backend cannot honestly satisfy that
 * shape without either faking capabilities `MediaStore` doesn't have, or documenting the
 * gap. This implementation does the latter:
 * - A `media:` id with row id `-1` is treated as a *collection root* (e.g. "all images") —
 *   [listChildren] on it queries the whole collection flat. Any other id is a leaf; its
 *   [listChildren] returns an empty success (a media item has no children — that's a
 *   correct answer, not a missing feature).
 * - [createDirectory] returns [FileError.InvalidDestination] — there is no MediaStore
 *   operation this could honestly map to. Reusing an existing `FileError` variant rather
 *   than inventing a new one Phase 2 didn't define.
 * - [StorageBackendContractTestForMediaStore] (this phase's test file) overrides the two
 *   inherited contract-test cases that assume directory creation, rather than pretending
 *   they pass.
 *
 * Confidence tier: same as [SafBackend] — real, stable `MediaStore` APIs, but Robolectric's
 * shadow coverage here is unproven, and this is genuinely new-to-this-project territory.
 */
class MediaStoreBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : StorageBackend {

    override val type: BackendType = BackendType.MEDIASTORE

    private val resolver: ContentResolver get() = context.contentResolver

    override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.MEDIA

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> {
        val ref = id.toMediaRef()
        if (ref.isCollectionRoot) return FileResult.Success(ref.toRootFileNode(id))

        val itemUri = ContentUris.withAppendedId(ref.collectionUri, ref.rowId)
        val cursor = try {
            resolver.query(itemUri, PROJECTION, null, null, null)
        } catch (e: SecurityException) {
            return FileResult.Failure(FileError.AccessDenied(null))
        } ?: return FileResult.Failure(FileError.ProviderUnavailable(itemUri.authority))

        return cursor.use {
            if (it.moveToFirst()) {
                FileResult.Success(it.toFileNode(ref, parentId = null))
            } else {
                FileResult.Failure(FileError.FileNotFound(null))
            }
        }
    }

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> = flow {
        val ref = id.toMediaRef()
        if (!ref.isCollectionRoot) {
            // A leaf media item has no children — correct answer, not a missing feature.
            emit(FileResult.Success(emptyList()))
            return@flow
        }

        val cursor = try {
            resolver.query(
                ref.collectionUri,
                PROJECTION,
                null,
                null,
                "${MediaStore.MediaColumns.DISPLAY_NAME} ASC",
            )
        } catch (e: SecurityException) {
            emit(FileResult.Failure(FileError.AccessDenied(null)))
            return@flow
        } ?: run {
            emit(FileResult.Failure(FileError.ProviderUnavailable(ref.collectionUri.authority)))
            return@flow
        }

        var emittedAny = false
        val chunk = ArrayList<FileNode>(LISTING_CHUNK_SIZE)
        try {
            cursor.use {
                while (it.moveToNext()) {
                    chunk.add(it.toFileNode(ref, parentId = id))
                    if (chunk.size >= LISTING_CHUNK_SIZE) {
                        emit(FileResult.Success(chunk.toList()))
                        emittedAny = true
                        chunk.clear()
                    }
                }
            }
            if (chunk.isNotEmpty() || !emittedAny) {
                emit(FileResult.Success(chunk.toList()))
            }
        } catch (e: RuntimeException) {
            if (!emittedAny) {
                emit(FileResult.Failure(FileError.ProviderUnavailable(ref.collectionUri.authority)))
            }
        }
    }

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
        val ref = id.toMediaRef()
        if (ref.isCollectionRoot) return FileResult.Failure(FileError.FileNotFound(null))
        val itemUri = ContentUris.withAppendedId(ref.collectionUri, ref.rowId)
        return try {
            val checkStream = resolver.openInputStream(itemUri)
                ?: return FileResult.Failure(FileError.IoFailure(null))
            checkStream.close()
            FileResult.Success(
                InputStreamProvider {
                    resolver.openInputStream(itemUri)
                        ?: error("MediaStore item vanished between check and read: $itemUri")
                }
            )
        } catch (e: SecurityException) {
            FileResult.Failure(FileError.AccessDenied(null))
        } catch (e: IOException) {
            FileResult.Failure(e.toFileError(ErrorContext(null)))
        }
    }

    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> {
        val parentRef = parent.toMediaRef()
        if (!parentRef.isCollectionRoot) return FileResult.Failure(FileError.InvalidDestination(name))

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime ?: "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val itemUri = try {
            resolver.insert(parentRef.collectionUri, values)
        } catch (e: SecurityException) {
            return FileResult.Failure(FileError.AccessDenied(name))
        } ?: return FileResult.Failure(FileError.AccessDenied(name))

        return FileResult.Success(MediaStoreOutputTarget(resolver, itemUri, parentRef, name))
    }

    /**
     * Not supported — see class KDoc. `MediaStore` has no directory-creation operation;
     * folder-like organisation only exists implicitly via `RELATIVE_PATH` on inserted
     * files, which isn't the same thing as a browsable, independently-creatable folder.
     */
    override suspend fun createDirectory(parent: FileNodeId, name: String): FileResult<FileNode> =
        FileResult.Failure(FileError.InvalidDestination(name))

    override suspend fun delete(id: FileNodeId): FileResult<Unit> {
        val ref = id.toMediaRef()
        if (ref.isCollectionRoot) return FileResult.Failure(FileError.InvalidDestination(null))
        val itemUri = ContentUris.withAppendedId(ref.collectionUri, ref.rowId)
        return try {
            val rows = resolver.delete(itemUri, null, null)
            if (rows > 0) FileResult.Success(Unit) else FileResult.Failure(FileError.FileNotFound(null))
        } catch (e: SecurityException) {
            // RecoverableSecurityException (API 29+) means the caller needs to launch
            // e.userAction.actionIntent and retry — a UI concern (Phase 6/7), not
            // something this backend resolves alone. The IntentSender itself has no
            // representation in the domain-level FileError vocabulary Phase 2 defined —
            // mapped to PermissionDenied(AccessTarget) as the closest existing signal that
            // "re-consent is needed," which loses the actual IntentSender. Flagged in
            // PHASE_3_NOTES.md as a real, not-yet-resolved gap. The SDK_INT check is
            // deliberate: referencing a class that doesn't exist below API 29 unconditionally
            // in an `is` check risks a class-verification failure on minSdk 27/28 — checking
            // SDK_INT before the type reference is the standard, safe pattern.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                FileResult.Failure(FileError.PermissionDenied(ref.accessTarget))
            } else {
                FileResult.Failure(FileError.AccessDenied(null))
            }
        }
    }

    /** `MediaStore` renames are a `DISPLAY_NAME` column update, not a filesystem op. */
    override suspend fun rename(id: FileNodeId, newName: String): FileResult<FileNode> {
        val ref = id.toMediaRef()
        if (ref.isCollectionRoot) return FileResult.Failure(FileError.InvalidDestination(newName))
        val itemUri = ContentUris.withAppendedId(ref.collectionUri, ref.rowId)
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        return try {
            val rows = resolver.update(itemUri, values, null, null)
            if (rows > 0) getNode(id) else FileResult.Failure(FileError.AccessDenied(newName))
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                FileResult.Failure(FileError.PermissionDenied(ref.accessTarget))
            } else {
                FileResult.Failure(FileError.AccessDenied(newName))
            }
        }
    }

    /** No real "parent" to move within — see class KDoc. Not supported. */
    override suspend fun moveWithin(id: FileNodeId, newParent: FileNodeId): FileResult<FileNode> =
        FileResult.Failure(FileError.InvalidDestination(null))

    override suspend fun exists(parent: FileNodeId, name: String): Boolean {
        val parentRef = parent.toMediaRef()
        if (!parentRef.isCollectionRoot) return false
        val cursor = try {
            resolver.query(
                parentRef.collectionUri,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(name),
                null,
            )
        } catch (e: SecurityException) {
            null
        } ?: return false
        return cursor.use { it.count > 0 }
    }

    override suspend fun freeSpace(id: FileNodeId): Long = 0L
    // Same reasoning as SafBackend: no portable free-space query at this level. The
    // *volume* the MediaStore item lives on has a real free-space number, but that's
    // com.devbehindyou.refract.data.volume.StorageVolumes' job, not this backend's.

    companion object {
        private val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

        /**
         * The synthetic root id for one collection (e.g. "all images") — the `-1` row-id
         * sentinel this backend treats as [MediaRef.isCollectionRoot]. Callers browsing
         * MediaStore (a future phase's Home-screen category tiles, most likely) need this
         * rather than constructing a `media:` id by hand.
         */
        fun rootId(volume: String, collection: String): FileNodeId = FileNodeId.media(volume, collection, -1L)
    }
}

internal data class MediaRef(
    val volume: String,
    val collection: String,
    val rowId: Long,
    val collectionUri: android.net.Uri,
) {
    val isCollectionRoot: Boolean get() = rowId < 0

    /** Best-effort mapping to the closest [AccessTarget] case — falls back to
     * [AccessTarget.AllFiles] for a collection string this backend doesn't recognise
     * (currently only reachable via the MediaStore.Files fallback in [toMediaRef]). */
    val accessTarget: AccessTarget
        get() = when (collection) {
            "images" -> AccessTarget.MediaImages
            "video" -> AccessTarget.MediaVideo
            "audio" -> AccessTarget.MediaAudio
            else -> AccessTarget.AllFiles
        }
}

/**
 * `internal`, not `private` — exposed so `MediaStoreIdMappingTest` can exercise this id
 * parsing/URI-construction logic directly, same reasoning as SafBackend's `toSafRef`.
 */
internal fun FileNodeId.toMediaRef(): MediaRef {
    require(prefix == FileNodeId.Prefix.MEDIA) { "MediaStoreBackend cannot handle $this" }
    val body = raw.removePrefix(FileNodeId.Prefix.MEDIA.scheme)
    val parts = body.split(":")
    val volume = parts[0]
    val collection = parts[1]
    val rowId = parts[2].toLong()
    val collectionUri = when (collection) {
        "images" -> MediaStore.Images.Media.getContentUri(volume)
        "video" -> MediaStore.Video.Media.getContentUri(volume)
        "audio" -> MediaStore.Audio.Media.getContentUri(volume)
        else -> MediaStore.Files.getContentUri(volume)
    }
    return MediaRef(volume, collection, rowId, collectionUri)
}

private fun MediaRef.toRootFileNode(id: FileNodeId): FileNode = FileNode(
    id = id,
    name = collection,
    displayName = collection.replaceFirstChar { it.uppercase() },
    mimeType = null,
    size = -1L,
    modifiedAt = 0L,
    isDirectory = true,
    isHidden = false,
    parentId = null,
    storageType = StorageType.MEDIA_INDEX,
    access = AccessFlags(readable = true, writable = true, deletable = false, renamable = false),
    childCount = null,
    extras = null,
)

private fun Cursor.toFileNode(ref: MediaRef, parentId: FileNodeId?): FileNode {
    val rowId = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
    val id = FileNodeId.media(ref.volume, ref.collection, rowId)
    val name = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) ?: rowId.toString()
    val mime = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
    val size = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
    val dateModifiedSeconds = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))

    return FileNode(
        id = id,
        name = name,
        displayName = name,
        mimeType = mime,
        size = size,
        // DATE_MODIFIED is documented as epoch *seconds*, not millis, unlike
        // java.io.File.lastModified() and DocumentsContract.COLUMN_LAST_MODIFIED, both of
        // which are millis — an easy, easy-to-miss unit mismatch across the three backends.
        modifiedAt = dateModifiedSeconds * 1000L,
        isDirectory = false,
        isHidden = name.startsWith("."),
        parentId = parentId,
        storageType = StorageType.MEDIA_INDEX,
        access = AccessFlags(readable = true, writable = true, deletable = true, renamable = true),
        childCount = null,
        extras = null,
    )
}

private class MediaStoreOutputTarget(
    private val resolver: ContentResolver,
    private val itemUri: android.net.Uri,
    private val ref: MediaRef,
    private val name: String,
) : OutputTarget {
    private var discarded = false

    override fun stream() = resolver.openOutputStream(itemUri, "wt")
        ?: error("MediaStore item has no writable stream: $itemUri")

    override fun setLastModified(epochMillis: Long) {
        // DATE_MODIFIED is normally provider-maintained on write, not client-settable
        // ahead of time in a way every OS version honours consistently — recorded here
        // only as an acknowledged no-op, same situation as SafOutputTarget.
    }

    override fun discard() {
        discarded = true
        runCatching { resolver.delete(itemUri, null, null) }
    }

    override fun sync() {
        // No portable fsync-equivalent over a MediaStore ContentResolver stream.
    }

    override suspend fun toNode(): FileResult<FileNode> {
        if (discarded) return FileResult.Failure(FileError.OperationCancelled)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val clearPending = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            runCatching { resolver.update(itemUri, clearPending, null, null) }
        }
        val cursor = try {
            resolver.query(
                itemUri,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                ),
                null,
                null,
                null,
            )
        } catch (e: SecurityException) {
            return FileResult.Failure(FileError.AccessDenied(name))
        } ?: return FileResult.Failure(FileError.ProviderUnavailable(itemUri.authority))

        return cursor.use {
            if (it.moveToFirst()) {
                FileResult.Success(it.toFileNode(ref, parentId = null))
            } else {
                FileResult.Failure(FileError.IoFailure(name))
            }
        }
    }
}
