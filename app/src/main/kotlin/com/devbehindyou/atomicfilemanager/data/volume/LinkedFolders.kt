package com.devbehindyou.atomicfilemanager.data.volume

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.devbehindyou.atomicfilemanager.data.backend.safId
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

/** A folder another app shared through the system picker (ALL_IN_ONE_PLAN.md 3.3). */
data class LinkedFolder(
    val treeUri: Uri,
    val rootId: FileNodeId,
    val name: String,
    val appLabel: String,
    val writable: Boolean,
)

/**
 * Folders from other apps, such as cloud apps that publish a `DocumentsProvider`, opened through
 * the system folder picker. Atomic File Manager never signs in to any service: the other app
 * does, and Android hands over only the folder the user picked. The list is Android's own record
 * of persisted grants, so nothing extra is stored and a grant revoked in Settings simply vanishes.
 */
class LinkedFolders(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun list(): List<LinkedFolder> =
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .mapNotNull { folder(it.uri, it.isWritePermission) }
            .sortedBy { it.name.lowercase() }

    /** Keeps access to [treeUri] across restarts; read-only when the app only allows reading. */
    fun link(treeUri: Uri): LinkedFolder? {
        val writable =
            runCatching {
                resolver.takePersistableUriPermission(treeUri, READ or WRITE)
                true
            }.getOrElse {
                runCatching { resolver.takePersistableUriPermission(treeUri, READ) }.getOrNull() ?: return null
                false
            }
        return folder(treeUri, writable)
    }

    fun unlink(folder: LinkedFolder) {
        val flags = if (folder.writable) READ or WRITE else READ
        runCatching { resolver.releasePersistableUriPermission(folder.treeUri, flags) }
    }

    private fun folder(
        treeUri: Uri,
        writable: Boolean,
    ): LinkedFolder? {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val rootId = safId(treeUri, rootDocId)
        val authority = treeUri.authority.orEmpty()
        return LinkedFolder(
            treeUri = treeUri,
            rootId = rootId,
            name = displayName(treeUri, rootDocId) ?: LinkedFolderText.fallbackName(rootDocId),
            appLabel = appLabel(authority) ?: authority,
            writable = writable,
        )
    }

    private fun displayName(
        treeUri: Uri,
        documentId: String,
    ): String? =
        runCatching {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun appLabel(authority: String): String? =
        runCatching {
            context.packageManager.resolveContentProvider(authority, 0)?.loadLabel(context.packageManager)?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val READ = Intent.FLAG_GRANT_READ_URI_PERMISSION
        const val WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/** Wording for linked folders; pure so it is unit-tested. */
object LinkedFolderText {
    /** "primary:Documents/Work" → "Work"; providers that use opaque ids just show the id. */
    fun fallbackName(documentId: String): String =
        documentId.substringAfter(':').trimEnd('/').substringAfterLast('/').ifBlank { documentId }

    fun meta(
        appLabel: String,
        writable: Boolean,
    ): String = if (writable) appLabel else "$appLabel · read only"

    const val EXPLAINER =
        "Cloud and other apps can share a folder here through Android's picker, without signing in to " +
            "Atomic File Manager. Only apps that offer whole folders appear; some share single files only."
}
