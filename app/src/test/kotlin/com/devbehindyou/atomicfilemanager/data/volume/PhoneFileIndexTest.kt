package com.devbehindyou.atomicfilemanager.data.volume

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneFileIndexTest {
    @Test
    fun scansAllSelectedRootsAndNestedFoldersWithoutDuplicateEntries() =
        runTest {
            val backend = InMemoryBackend()

            suspend fun folder(
                parent: FileNodeId,
                name: String,
            ) = (backend.createDirectory(parent, name) as FileResult.Success).value.id

            suspend fun file(
                parent: FileNodeId,
                name: String,
            ) {
                val target = (backend.openOutput(parent, name, "application/octet-stream") as FileResult.Success).value
                target.stream().use { it.write(1) }
                target.sync()
                target.toNode()
            }
            val internal = folder(backend.rootId, "internal")
            val sd = folder(backend.rootId, "sd")
            val nested = folder(internal, "messenger")
            file(nested, "photo.jpg")
            file(sd, "movie.mkv")
            file(backend.rootId, "outside.apk")
            val index = PhoneFileIndex(GetDirectoryListingUseCase { backend })
            val roots = listOf(internal, sd, nested)
            val first = index.scan(roots).last()
            assertTrue(first.complete)
            assertEquals(setOf("photo.jpg", "movie.mkv"), first.files.map { it.name }.toSet())
            assertEquals(2, first.files.size)
            file(sd, "new.apk")
            // Served from the cache until a refresh or invalidation is requested.
            assertEquals(2, index.scan(roots).last().files.size)
            val refreshed = index.scan(roots, refresh = true).last()
            assertEquals(3, refreshed.files.size)
            file(sd, "later.apk")
            index.invalidate()
            assertEquals(4, index.scan(roots).last().files.size)
        }

    @Test
    fun showsKnownFilesFirstThenOnlyTheFinishedWalk() =
        runTest {
            val backend = InMemoryBackend()
            val root = (backend.createDirectory(backend.rootId, "internal") as FileResult.Success).value.id
            val target = (backend.openOutput(root, "real.jpg", "image/jpeg") as FileResult.Success).value
            target.stream().use { it.write(1) }
            target.sync()
            val stale = (target.toNode() as FileResult.Success).value.copy(id = FileNodeId.file("/gone.jpg"), name = "gone.jpg")
            var asked = 0
            val index =
                PhoneFileIndex(GetDirectoryListingUseCase { backend }) {
                    asked++
                    listOf(stale)
                }
            val emitted = index.scan(listOf(root)).toList()
            assertEquals(listOf("gone.jpg"), emitted.first().files.map { it.name })
            assertFalse(emitted.first().complete)
            // No partial walk results in between: the seed is replaced only by the complete walk.
            assertEquals(2, emitted.size)
            assertTrue(emitted.last().complete)
            assertEquals(listOf("real.jpg"), emitted.last().files.map { it.name })
            // A manual refresh never shows the seed.
            index.scan(listOf(root), refresh = true).toList()
            assertEquals(1, asked)
        }

    @Test
    fun skipsHiddenFilesAndEverythingInsideHiddenFolders() =
        runTest {
            val backend = InMemoryBackend()
            val root = (backend.createDirectory(backend.rootId, "internal") as FileResult.Success).value.id
            val movies = (backend.createDirectory(root, "Movies") as FileResult.Success).value.id
            val thumbnails = (backend.createDirectory(movies, ".thumbnails") as FileResult.Success).value.id
            for ((parent, name) in listOf(movies to "clip.mp4", movies to ".nomedia", thumbnails to "1234.jpg")) {
                val target = (backend.openOutput(parent, name, "application/octet-stream") as FileResult.Success).value
                target.stream().use { it.write(1) }
                target.sync()
            }
            val listed = mutableListOf<FileNodeId>()
            val index =
                PhoneFileIndex(
                    GetDirectoryListingUseCase { id ->
                        listed += id
                        backend
                    },
                )
            // In-memory ids are flat (/mem/1, /mem/2, ...), so a child is never under its parent's path.
            // Adding their common parent as a root puts every node inside the scan boundary; listing
            // /mem itself fails and only counts as unreadable.
            val snapshot = index.scan(listOf(FileNodeId.file("/mem"), root)).last()
            assertTrue(snapshot.complete)
            assertEquals(listOf("clip.mp4"), snapshot.files.map { it.name })
            assertTrue(movies in listed)
            assertFalse(thumbnails in listed)
        }
}
