package com.devbehindyou.refract.data.volume

import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.testing.InMemoryBackend
import com.devbehindyou.refract.domain.usecase.GetDirectoryListingUseCase
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
}
