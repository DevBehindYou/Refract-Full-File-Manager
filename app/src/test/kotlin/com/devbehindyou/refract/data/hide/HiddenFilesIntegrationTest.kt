package com.devbehindyou.refract.data.hide

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.refract.data.backend.FileSystemBackend
import com.devbehindyou.refract.data.database.HiddenFilesDatabaseHelper
import com.devbehindyou.refract.data.repository.HiddenFilesRepositoryImpl
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class HiddenFilesIntegrationTest {
    @get:Rule
    val temporary = TemporaryFolder()
    private lateinit var database: HiddenFilesDatabaseHelper
    private lateinit var repository: HiddenFilesRepositoryImpl
    private lateinit var backend: FileSystemBackend

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = HiddenFilesDatabaseHelper(context, null)
        repository = HiddenFilesRepositoryImpl(context, database)
        backend = FileSystemBackend(context)
    }

    @After
    fun close() = database.close()

    private suspend fun node(file: File): FileNode =
        (
            backend.getNode(
                FileNodeId.file(file.absolutePath),
            ) as FileResult.Success
        ).value

    @Test
    fun galleryHideRestoresBytesUsingRealFileNodeId() =
        runBlocking {
            val file = temporary.newFile("photo.jpg").apply { writeBytes(ByteArray(1024) { it.toByte() }) }
            val bytes = file.readBytes()
            val item = repository.hideFromGallery(node(file)).getOrThrow()
            assertFalse(file.exists())
            assertTrue(File(File(item.currentLocation).parentFile, ".nomedia").exists())
            repository.unhideFromGallery(item).getOrThrow()
            assertArrayEquals(bytes, file.readBytes())
        }

    @Test
    fun obscureRestoresBytesThroughRepository() =
        runBlocking {
            val file = temporary.newFile("secret.bin").apply { writeBytes(ByteArray(1025) { it.toByte() }) }
            val bytes = file.readBytes()
            val item = repository.fastObscure(node(file)).getOrThrow()
            repository.restoreFastObscured(item).getOrThrow()
            assertArrayEquals(bytes, file.readBytes())
            assertTrue(repository.hiddenItems.value.isEmpty())
        }

    @Test
    fun privateMoveRestoresBytesUsingRealFileNodeId() =
        runBlocking {
            val file = temporary.newFile("private.bin").apply { writeBytes(ByteArray(1024) { it.toByte() }) }
            val bytes = file.readBytes()
            val item = repository.moveToPrivateStorage(node(file)).getOrThrow()
            assertFalse(file.exists())
            repository.restoreFromPrivateStorage(item, FileNodeId.file(temporary.root.absolutePath)).getOrThrow()
            assertArrayEquals(bytes, file.readBytes())
        }

    @Test
    fun missingHiddenFileDoesNotEraseRecoveryMetadata() =
        runBlocking {
            val file = temporary.newFile("missing.jpg").apply { writeText("photo") }
            val item = repository.hideFromGallery(node(file)).getOrThrow()
            assertTrue(File(item.currentLocation).delete())
            assertTrue(repository.unhideFromGallery(item).isFailure)
            assertTrue(repository.hiddenItems.value.any { it.id == item.id })
        }
}
