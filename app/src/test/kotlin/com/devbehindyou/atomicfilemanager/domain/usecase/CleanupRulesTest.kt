package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CleanupRulesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun node(
        path: String,
        size: Long = 1,
        ageDays: Long = 0,
        directory: Boolean = false,
    ) = FileNode(
        id = FileNodeId.file(path),
        name = path.substringAfterLast('/'),
        displayName = path.substringAfterLast('/'),
        mimeType = null,
        size = size,
        modifiedAt = now - ageDays * day,
        isDirectory = directory,
        isHidden = false,
        parentId = null,
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL,
        childCount = null,
        extras = null,
    )

    @Test
    fun `old screenshots are found by folder or name and age`() {
        assertTrue(CleanupRules.isOldScreenshot(node("/sdcard/Pictures/Screenshots/a.png", ageDays = 31), now))
        assertTrue(CleanupRules.isOldScreenshot(node("/sdcard/DCIM/Screenshot_2025.jpg", ageDays = 40), now))
        assertFalse(CleanupRules.isOldScreenshot(node("/sdcard/Pictures/Screenshots/a.png", ageDays = 3), now))
        assertFalse(CleanupRules.isOldScreenshot(node("/sdcard/DCIM/Camera/a.jpg", ageDays = 400), now))
        assertFalse(CleanupRules.isOldScreenshot(node("/sdcard/Pictures/Screenshots/b.png").copy(modifiedAt = 0), now))
    }

    @Test
    fun `old downloads must be big, old and in Download`() {
        val big = CleanupRules.DOWNLOAD_MIN_BYTES
        assertTrue(CleanupRules.isOldDownload(node("/sdcard/Download/movie.mkv", big, ageDays = 91), now))
        assertFalse(CleanupRules.isOldDownload(node("/sdcard/Download/movie.mkv", big - 1, ageDays = 91), now))
        assertFalse(CleanupRules.isOldDownload(node("/sdcard/Download/movie.mkv", big, ageDays = 10), now))
        assertFalse(CleanupRules.isOldDownload(node("/sdcard/Movies/movie.mkv", big, ageDays = 200), now))
        assertFalse(CleanupRules.isOldDownload(node("/sdcard/Download/Folder", big, 200, directory = true), now))
    }

    @Test
    fun `only empty folders and temp files are preselected and every card has a reason`() {
        val safe = StorageAnalysisCategory.entries.filter(CleanupRules::preselected)
        assertEquals(listOf(StorageAnalysisCategory.EMPTY_FOLDERS, StorageAnalysisCategory.TEMP_AND_CACHE), safe)
        StorageAnalysisCategory.entries.forEach { assertTrue(CleanupRules.reason(it).isNotBlank()) }
    }

    @Test
    fun `analyzer lists old screenshots and apks for installed apps`() =
        runTest {
            val backend = InMemoryBackend()

            suspend fun put(
                name: String,
                modified: Long,
            ) {
                val out = (backend.openOutput(backend.rootId, name, null) as FileResult.Success).value
                // The in-memory target commits when its stream closes, so the date goes first.
                out.setLastModified(modified)
                out.stream().use { it.write(name.toByteArray()) }
                out.sync()
                out.toNode()
            }
            put("Screenshot_old.png", now - 60 * day)
            put("Screenshot_new.png", now - day)
            put("chat.apk", now)
            put("game.apk", now)
            val analyzer =
                StorageAnalyzerUseCase(
                    backendSelector = { backend },
                    readFileContentUseCase = ReadFileContentUseCase { backend },
                    isInstalledApk = { it.name == "chat.apk" },
                    clock = { now },
                )

            val result = analyzer.getFullAnalysis(backend.rootId)

            assertEquals(listOf("Screenshot_old.png"), result.oldScreenshots.map { it.name })
            assertEquals(listOf("chat.apk"), result.installedApks.map { it.name })
        }
}
