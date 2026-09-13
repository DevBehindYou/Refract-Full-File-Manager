package com.devbehindyou.refract.data.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class MediaMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val artwork: Bitmap?,
    val mimeType: String?,
)

data class PreparedMedia(
    val filePath: String,
    val metadata: MediaMetadata,
    val cleanup: () -> Unit,
)

class MediaPreviewHelper(
    private val context: Context,
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    suspend fun prepareMedia(
        id: FileNodeId,
        fileName: String,
    ): FileResult<PreparedMedia> =
        withContext(Dispatchers.IO) {
            val tempFile =
                copyToCache(id, fileName)
                    ?: return@withContext FileResult.Failure(FileError.IoFailure("Failed to cache media for playback"))

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tempFile.absolutePath)
                val metadata = extractMetadata(retriever)
                FileResult.Success(
                    PreparedMedia(
                        filePath = tempFile.absolutePath,
                        metadata = metadata,
                        cleanup = { tempFile.delete() },
                    ),
                )
            } catch (e: Exception) {
                tempFile.delete()
                FileResult.Failure(FileError.IoFailure(e.message ?: "Failed reading media metadata"))
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {
                    // Ignore release failure on older API runtimes
                }
            }
        }

    suspend fun extractVideoThumbnail(
        id: FileNodeId,
        fileName: String,
    ): FileResult<Bitmap> =
        withContext(Dispatchers.IO) {
            val tempFile =
                copyToCache(id, fileName)
                    ?: return@withContext FileResult.Failure(FileError.IoFailure("Failed to cache video for thumbnail"))

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tempFile.absolutePath)
                val frame =
                    retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                if (frame != null) {
                    FileResult.Success(frame)
                } else {
                    FileResult.Failure(FileError.IoFailure("Could not extract video frame"))
                }
            } catch (e: Exception) {
                FileResult.Failure(FileError.IoFailure(e.message ?: "Failed extracting video frame"))
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {
                    // Ignore release failure on older API runtimes
                }
                tempFile.delete()
            }
        }

    private fun extractMetadata(retriever: MediaMetadataRetriever): MediaMetadata {
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = durationStr?.toLongOrNull() ?: 0L
        val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val width = widthStr?.toIntOrNull() ?: 0
        val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val height = heightStr?.toIntOrNull() ?: 0
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

        val artBytes = retriever.embeddedPicture
        val artwork =
            if (artBytes != null && artBytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            } else {
                null
            }

        return MediaMetadata(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            width = width,
            height = height,
            artwork = artwork,
            mimeType = mime,
        )
    }

    private suspend fun copyToCache(
        id: FileNodeId,
        fileName: String,
    ): File? =
        withContext(Dispatchers.IO) {
            val backend = backendSelector(id)
            val inRes = backend.openInput(id)
            if (inRes !is FileResult.Success) return@withContext null

            val extension = fileName.substringAfterLast('.', "tmp")
            val cacheFile = File(context.cacheDir, "media_preview_${System.currentTimeMillis()}.$extension")
            try {
                inRes.value.stream().use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                cacheFile
            } catch (e: IOException) {
                cacheFile.delete()
                null
            }
        }
}
