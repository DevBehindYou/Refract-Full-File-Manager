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
            val regex =
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
            return rows.values
                .filter { regex.matches(it.nameLower) }
                .sortedWith(compareByDescending<SearchIndexEntity> { it.isDirectory }.thenBy { it.nameLower })
                .take(limit)
        }

        override suspend fun deleteOlderThan(generation: Long): Int {
            val old = rows.values.filter { it.generation < generation }.map { it.id }
            old.forEach(rows::remove)
            return old.size
        }

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
}
