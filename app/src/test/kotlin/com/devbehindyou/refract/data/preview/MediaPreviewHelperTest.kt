package com.devbehindyou.refract.data.preview

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.testing.InMemoryBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MediaPreviewHelperTest {
    private lateinit var context: Context
    private lateinit var backend: InMemoryBackend
    private lateinit var helper: MediaPreviewHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        backend = InMemoryBackend()
        helper = MediaPreviewHelper(context) { backend }
    }

    @Test
    fun prepareMedia_nonExistentFile_returnsFailure() =
        runBlocking {
            val badId = FileNodeId("file:/non/existent/audio.mp3")
            val result = helper.prepareMedia(badId, "audio.mp3")

            assertTrue("Expected failure for non-existent file", result is FileResult.Failure)
            val error = (result as FileResult.Failure).error
            assertTrue(error is FileError.IoFailure)
        }

    @Test
    fun extractVideoThumbnail_nonExistentFile_returnsFailure() =
        runBlocking {
            val badId = FileNodeId("file:/non/existent/video.mp4")
            val result = helper.extractVideoThumbnail(badId, "video.mp4")

            assertTrue("Expected failure for non-existent video", result is FileResult.Failure)
            val error = (result as FileResult.Failure).error
            assertTrue(error is FileError.IoFailure)
        }

    @Test
    fun prepareMedia_cleanupDeletesTempFile() =
        runBlocking {
            val outTarget = (backend.openOutput(backend.rootId, "sample.mp3", "audio/mpeg") as FileResult.Success).value
            outTarget.stream().use { it.write("fake-audio-bytes".toByteArray()) }
            outTarget.sync()
            val fileNode = (outTarget.toNode() as FileResult.Success).value

            // In Robolectric, MediaMetadataRetriever may fail on dummy bytes and throw,
            // which triggers catch { tempFile.delete(); FileResult.Failure(...) }.
            // Let's verify that no temp files leak in cacheDir.
            val cacheFilesBefore = context.cacheDir.listFiles()?.toList() ?: emptyList()

            val result = helper.prepareMedia(fileNode.id, "sample.mp3")

            if (result is FileResult.Success) {
                val prepared = result.value
                val tempFile = File(prepared.filePath)
                assertTrue("Temp file should exist before cleanup", tempFile.exists())
                prepared.cleanup()
                assertFalse("Temp file should be deleted after cleanup", tempFile.exists())
            } else {
                // When MediaMetadataRetriever fails on dummy bytes, verify temp file was deleted in catch block
                val cacheFilesAfter = context.cacheDir.listFiles()?.toList() ?: emptyList()
                assertEquals("No temp file should leak in cacheDir", cacheFilesBefore.size, cacheFilesAfter.size)
            }
        }
}
