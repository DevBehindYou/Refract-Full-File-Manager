package com.devbehindyou.refract

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.refract.data.backend.FileSystemBackend
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileOperation
import com.devbehindyou.refract.domain.model.OperationId
import com.devbehindyou.refract.domain.model.OperationOptions
import com.devbehindyou.refract.domain.model.OperationStatus
import com.devbehindyou.refract.domain.model.OperationType
import com.devbehindyou.refract.domain.usecase.FileOperationsEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Exercises actual volume boundaries using only disposable app-owned directories. */
@RunWith(AndroidJUnit4::class)
class RemovableStorageDeviceTest {
    @Test
    fun moveToRemovableStorageAndBackPreservesBytes() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val removable =
                context.getExternalFilesDirs(null).filterNotNull()
                    .firstOrNull { Environment.isExternalStorageRemovable(it) }
            assumeNotNull(removable)
            val name = "verification-${UUID.randomUUID()}"
            val internal = File(context.cacheDir, name).apply { check(mkdir()) }
            val external = File(requireNotNull(removable), name).apply { check(mkdir()) }
            try {
                val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
                val source = File(internal, "payload.bin").apply { writeBytes(bytes) }
                val backend = FileSystemBackend(context)

                suspend fun move(
                    file: File,
                    parent: File,
                ) {
                    val request =
                        FileOperation(
                            id = OperationId.random(),
                            type = OperationType.MOVE,
                            sources = listOf(FileNodeId.file(file.absolutePath)),
                            destination = FileNodeId.file(parent.absolutePath),
                            options = OperationOptions(),
                            createdAt = 0,
                        )
                    val result = FileOperationsEngine { backend }.execute(request).toList().last().status
                    assertTrue("Expected completed move, received $result", result is OperationStatus.Completed)
                    assertFalse(file.exists())
                }
                move(source, external)
                val onCard = File(external, source.name)
                assertArrayEquals(bytes, onCard.readBytes())
                move(onCard, internal)
                assertArrayEquals(bytes, source.readBytes())
                assertTrue(external.listFiles()!!.isEmpty())
                assertTrue(internal.listFiles()!!.size == 1)
            } finally {
                internal.deleteRecursively()
                external.deleteRecursively()
            }
        }
}
