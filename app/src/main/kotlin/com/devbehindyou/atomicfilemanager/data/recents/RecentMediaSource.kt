package com.devbehindyou.atomicfilemanager.data.recents

import android.content.Context
import android.provider.MediaStore
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.RecentItem
import com.devbehindyou.atomicfilemanager.domain.model.RecentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Recently added or changed photos, videos and audio from MediaStore (ALL_IN_ONE_PLAN.md 1.2).
 * Reads only a small projection and stops after [limit] rows (screens/HOME.md §11). Without
 * media or all-files access it returns nothing instead of failing. Items under hidden folders
 * (the Trash, thumbnails) and files that no longer exist are skipped.
 */
class RecentMediaSource(private val context: Context) {
    suspend fun query(
        sinceMillis: Long,
        limit: Int,
    ): List<RecentItem> =
        withContext(Dispatchers.IO) {
            val projection =
                arrayOf(
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                )
            val selection =
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?) AND " +
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?"
            val args =
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(),
                    (sinceMillis / MILLIS_PER_SECOND).toString(),
                )
            val items = mutableListOf<RecentItem>()
            try {
                context.contentResolver
                    .query(
                        MediaStore.Files.getContentUri("external"),
                        projection,
                        selection,
                        args,
                        "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
                    )?.use { cursor ->
                        while (items.size < limit && cursor.moveToNext()) {
                            val path = cursor.getString(0)
                            val visible =
                                path != null && path.split('/').none { it.startsWith('.') } && File(path).isFile
                            if (visible) {
                                items +=
                                    RecentItem(
                                        id = FileNodeId.file(checkNotNull(path)),
                                        name = cursor.getString(1) ?: path.substringAfterLast('/'),
                                        mimeType = cursor.getString(2),
                                        size = if (cursor.isNull(3)) -1 else cursor.getLong(3),
                                        at = cursor.getLong(4) * MILLIS_PER_SECOND,
                                        source = RecentSource.MEDIA,
                                    )
                            }
                        }
                    }
            } catch (_: SecurityException) {
                // No media access yet: Home simply shows the files opened in the app.
            } catch (_: IllegalArgumentException) {
                // A provider without one of the columns.
            }
            items
        }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
