package com.devbehindyou.refract.data.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfPreviewHelper(
    private val context: Context,
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    suspend fun getPageCount(id: FileNodeId): FileResult<Int> =
        withContext(Dispatchers.IO) {
            val tempFile =
                copyToCache(id)
                    ?: return@withContext FileResult.Failure(FileError.IoFailure("Failed to cache PDF"))
            try {
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        FileResult.Success(renderer.pageCount)
                    }
                }
            } catch (e: Exception) {
                FileResult.Failure(FileError.IoFailure(e.message ?: "Failed reading PDF"))
            } finally {
                tempFile.delete()
            }
        }

    suspend fun renderPage(
        id: FileNodeId,
        pageIndex: Int,
        maxDimension: Int = 1600,
    ): FileResult<Bitmap> =
        withContext(Dispatchers.IO) {
            val tempFile =
                copyToCache(id)
                    ?: return@withContext FileResult.Failure(FileError.IoFailure("Failed to cache PDF"))
            try {
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                            return@withContext FileResult.Failure(FileError.IoFailure("Page $pageIndex out of range"))
                        }

                        renderer.openPage(pageIndex).use { page ->
                            val originalWidth = page.width
                            val originalHeight = page.height
                            val scale =
                                if (originalWidth > originalHeight) {
                                    maxDimension.toFloat() / originalWidth.toFloat()
                                } else {
                                    maxDimension.toFloat() / originalHeight.toFloat()
                                }.coerceAtMost(2.5f).coerceAtLeast(0.5f)

                            val targetWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
                            val targetHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)

                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            FileResult.Success(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                FileResult.Failure(FileError.IoFailure(e.message ?: "Failed rendering PDF page"))
            } finally {
                tempFile.delete()
            }
        }

    private suspend fun copyToCache(id: FileNodeId): File? =
        withContext(Dispatchers.IO) {
            val backend = backendSelector(id)
            val inRes = backend.openInput(id)
            if (inRes !is FileResult.Success) return@withContext null

            val cacheFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
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
