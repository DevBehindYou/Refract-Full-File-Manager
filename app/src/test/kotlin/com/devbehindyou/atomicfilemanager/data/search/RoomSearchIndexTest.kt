package com.devbehindyou.atomicfilemanager.data.search

import com.devbehindyou.atomicfilemanager.data.database.room.SearchIndexDao
import com.devbehindyou.atomicfilemanager.data.database.room.SearchIndexEntity
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomSearchIndexTest {
    /** Keeps rows in memory and evaluates LIKE with `\` as the escape character, like SQLite. */
    private class FakeDao : SearchIndexDao {
        val rows = linkedMapOf<String, SearchIndexEntity>()

        override suspend fun upsert(rows: List<SearchIndexEntity>) = rows.forEach { this.rows[it.id] = it }

        override suspend fun search(
            pattern: String,
            limit: Int,
        ): List<SearchIndexEntity> {
            val regex = like(pattern)
            return rows.values
                .filter { regex.matches(it.nameLower) }
                .sortedWith(compareByDescending<SearchIndexEntity> { it.isDirectory }.thenBy { it.nameLower })
                .take(limit)
        }

        override suspend fun idsIn(parentId: String) = rows.values.filter { it.parentId == parentId }.map { it.id }

        override suspend fun deleteTree(
            id: String,
            descendants: String,
        ) {
            val under = like(descendants)
            rows.keys.filter { it == id || under.matches(it) }.forEach(rows::remove)
        }

        private fun like(pattern: String): Regex =
            buildString {
                var escaped = false
                pattern.forEach { c ->
                    when {
                        escaped -> append(Regex.escape(c.toString())).also { escaped = false }
                        c == '\\' -> escaped = true
                        c == '%' -> append(".*")
                        c == '_' -> append(".")
                        else -> append(Regex.escape(c.toString()))
                    }
                }
            }.toRegex()

        override suspend fun deleteOlderThan(generation: Long): Int {
            val old = rows.values.filter { it.generation < generation }.map { it.id }
            old.forEach(rows::remove)
            return old.size
        }

        override suspend fun allFiles() = rows.values.filterNot { it.isDirectory }

        override suspend fun count() = rows.size
    }

    private class Stamp : RoomSearchIndex.BuiltAtStore {
        var value: Long? = null

        override fun get() = value

        override fun set(value: Long) {
            this.value = value
        }
    }

    private val backend = InMemoryBackend()
    private val dao = FakeDao()
    private val stamp = Stamp()
    private var now = 1_000L

    private fun TestScope.index() =
        RoomSearchIndex(
            dao = dao,
            listChildren = backend::listChildren,
            scope = backgroundScope,
            // In-memory ids are not paths; give every node a unique path under one root.
            canonicalPath = { id -> if (id == backend.rootId) "/r" else "/r/${id.raw}" },
            builtAtStore = stamp,
            clock = { now },
            freshForMillis = 10_000L,
        )

    private suspend fun folder(
        parent: FileNodeId,
        name: String,
    ) = (backend.createDirectory(parent, name) as FileResult.Success).value.id

    @Test
    fun `a build finds files and folders at any depth, but nothing hidden`() =
        runTest {
            val docs = folder(backend.rootId, "Docs")
            val deep = folder(docs, "2026 Reports")
            backend.putFile(deep, "Quarterly-Report.PDF", byteArrayOf(1))
            val hidden = folder(backend.rootId, ".Atomic File Manager")
            backend.putFile(hidden, "report-in-trash.pdf", byteArrayOf(1))
            backend.putFile(backend.rootId, ".report.tmp", byteArrayOf(1))
            val index = index()

            index.build(listOf(backend.rootId))

            assertEquals(listOf("2026 Reports", "Quarterly-Report.PDF"), index.search("REPORT", 10).map { it.name })
            assertEquals(deep, index.search("quarterly", 10).single().parentId)
            assertTrue(index.search("trash", 10).isEmpty())
            assertEquals(1_000L, stamp.value)
        }

    @Test
    fun `a rebuild drops what is gone and keeps the rest`() =
        runTest {
            val gone = backend.putFile(backend.rootId, "old-notes.txt", byteArrayOf(1))
            backend.putFile(backend.rootId, "notes.txt", byteArrayOf(1))
            val index = index()
            index.build(listOf(backend.rootId))
            backend.delete(gone)
            now = 2_000L

            index.build(listOf(backend.rootId))

            assertEquals(listOf("notes.txt"), index.search("notes", 10).map { it.name })
            assertEquals(1, index.state.value.indexedCount)
        }

    @Test
    fun `refreshIfStale builds only when there is no index or it is old`() =
        runTest {
            backend.putFile(backend.rootId, "a.txt", byteArrayOf(1))
            val index = index()

            index.refreshIfStale(listOf(backend.rootId))
            runCurrent()
            assertEquals(1_000L, stamp.value)
            assertFalse(index.state.value.building)

            now = 5_000L
            index.refreshIfStale(listOf(backend.rootId))
            runCurrent()
            assertEquals(1_000L, stamp.value)

            now = 20_000L
            index.refreshIfStale(listOf(backend.rootId))
            runCurrent()
            assertEquals(20_000L, stamp.value)
        }

    @Test
    fun `blank text searches nothing`() =
        runTest {
            backend.putFile(backend.rootId, "a.txt", byteArrayOf(1))
            val index = index()
            index.build(listOf(backend.rootId))
            assertTrue(index.search("   ", 10).isEmpty())
        }

    @Test
    fun `refreshing a folder adds new entries and drops removed ones with what was below them`() =
        runTest {
            val docs = folder(backend.rootId, "Docs")
            val old = folder(docs, "Old")
            backend.putFile(old, "inside-old.txt", byteArrayOf(1))
            backend.putFile(docs, "keep.txt", byteArrayOf(1))
            val index = index()
            index.build(listOf(backend.rootId))
            // The in-memory backend's ids are not paths, so seed the "descendant" the way a path id would look.
            dao.upsert(
                listOf(
                    dao.rows.values.first {
                        it.name == "keep.txt"
                    }.copy(id = "${old.raw}/deep.txt", name = "deep.txt", nameLower = "deep.txt"),
                ),
            )
            backend.delete(old)
            backend.putFile(docs, "new.txt", byteArrayOf(1))

            index.refreshFolders(listOf(docs))

            val names = dao.rows.values.map { it.name }.toSet()
            assertTrue("new.txt" in names)
            assertTrue("keep.txt" in names)
            assertFalse("Old" in names)
            assertFalse("deep.txt" in names)
        }

    @Test
    fun `nothing is refreshed before the first full build`() =
        runTest {
            backend.putFile(backend.rootId, "a.txt", byteArrayOf(1))
            index().refreshFolders(listOf(backend.rootId))
            assertTrue(dao.rows.isEmpty())
        }

    @Test
    fun `files lists indexed files under the given roots once built`() =
        runTest {
            val docs = folder(backend.rootId, "Docs")
            backend.putFile(docs, "a.pdf", byteArrayOf(1))
            backend.putFile(backend.rootId, "b.txt", byteArrayOf(1))
            val index = index()
            assertTrue(index.files(listOf(FileNodeId.file("/mem"))).isEmpty())

            index.build(listOf(backend.rootId))

            // In-memory ids are flat (/mem/0, /mem/1, ...), so their common parent stands in for the volume.
            val volume = FileNodeId.file("/mem")
            assertEquals(setOf("a.pdf", "b.txt"), index.files(listOf(volume)).map { it.name }.toSet())
            assertTrue(index.files(listOf(FileNodeId.file("/elsewhere"))).isEmpty())
        }
}
