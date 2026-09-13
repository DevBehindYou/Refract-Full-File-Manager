package com.devbehindyou.refract.data.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream

class ImagePreviewHelper(
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    suspend fun decodeImage(
        id: FileNodeId,
        maxDimension: Int = 2048,
    ): FileResult<Bitmap> =
        withContext(Dispatchers.IO) {
            val backend = backendSelector(id)
            val inRes = backend.openInput(id)
            val inProvider =
                when (inRes) {
                    is FileResult.Success -> inRes.value
                    is FileResult.Failure -> return@withContext FileResult.Failure(inRes.error)
                }

            try {
                val options =
                    BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                inProvider.stream().use { stream ->
                    BitmapFactory.decodeStream(BufferedInputStream(stream), null, options)
                }

                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    return@withContext FileResult.Failure(FileError.UnsupportedFormat("image"))
                }

                var sampleSize = 1
                val maxOut = maxOf(options.outWidth, options.outHeight)
                while ((maxOut / sampleSize) > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions =
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                val secondInRes = backend.openInput(id)
                val secondProvider =
                    when (secondInRes) {
                        is FileResult.Success -> secondInRes.value
                        is FileResult.Failure -> return@withContext FileResult.Failure(secondInRes.error)
                    }

                val bitmap =
                    secondProvider.stream().use { stream ->
                        BitmapFactory.decodeStream(BufferedInputStream(stream), null, decodeOptions)
                    }

                if (bitmap != null) {
                    FileResult.Success(bitmap)
                } else {
                    FileResult.Failure(FileError.IoFailure("Failed to decode image"))
                }
            } catch (e: Exception) {
                FileResult.Failure(FileError.IoFailure(id.raw))
            }
        }
}
