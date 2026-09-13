package com.devbehindyou.refract

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.refract.data.backend.FileSystemBackend
import com.devbehindyou.refract.data.database.TransferBubbleDatabaseHelper
import com.devbehindyou.refract.data.repository.TransferBubbleRepositoryImpl
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileOperation
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.model.OperationId
import com.devbehindyou.refract.domain.model.OperationOptions
import com.devbehindyou.refract.domain.model.OperationStatus
import com.devbehindyou.refract.domain.model.OperationType
import com.devbehindyou.refract.domain.usecase.FileOperationsEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Device-only journeys use disposable app-private fixtures; never touch user files. */
@RunWith(AndroidJUnit4::class)
class StorageJourneyInstrumentedTest {
    @Test
    fun activitySurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }

    @Test
    fun verifiedCopyPreservesSourceAndCopiesActualBytes() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val root = File(context.cacheDir, "verification-${UUID.randomUUID()}").apply { check(mkdir()) }
            try {
                val source = File(root, "source.bin").apply { writeBytes(ByteArray(256 * 1024) { it.toByte() }) }
                val destination = File(root, "destination").apply { check(mkdir()) }
                val backend = FileSystemBackend(context)
                val operation =
                    FileOperation(
                        id = OperationId.random(),
                        type = OperationType.COPY,
                        sources = listOf(FileNodeId.file(source.absolutePath)),
                        destination = FileNodeId.file(destination.absolutePath),
                        options = OperationOptions(),
                        createdAt = 0,
                    )
                val status = FileOperationsEngine { backend }.execute(operation).toList().last().status
                assertTrue(status is OperationStatus.Completed)
                assertArrayEquals(source.readBytes(), File(destination, source.name).readBytes())
                assertEquals(listOf(source.name), destination.listFiles()!!.map { it.name })
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun stagedBubbleSurvivesDatabaseReopenWithoutMovingSource() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "verification-${UUID.randomUUID()}.db"
            val source = File(context.cacheDir, "verification-${UUID.randomUUID()}.txt").apply { writeText("staged") }
            try {
                val node =
                    (
                        FileSystemBackend(
                            context,
                        ).getNode(FileNodeId.file(source.absolutePath)) as FileResult.Success
                    ).value
                val id =
                    TransferBubbleDatabaseHelper(context, name).use { database ->
                        val repository = TransferBubbleRepositoryImpl(database)
                        val bubble = repository.createBubble("Verification").getOrThrow()
                        repository.addItemsToBubble(bubble.id, listOf(node)).getOrThrow()
                        bubble.id
                    }
                TransferBubbleDatabaseHelper(context, name).use { database ->
                    val repository = TransferBubbleRepositoryImpl(database)
                    assertEquals(node.id, repository.getBubble(id)!!.items.single().fileNodeId)
                }
                assertEquals("staged", source.readText())
            } finally {
                source.delete()
                context.deleteDatabase(name)
            }
        }
}
