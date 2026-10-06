package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisCategory

/**
 * Smart cleanup rules (ALL_IN_ONE_PLAN.md 2.6): which files each card suggests, and the reason it
 * gives. Pure so they are unit-tested. Only the safe cards start with their items selected.
 */
object CleanupRules {
    const val SCREENSHOT_AGE_DAYS = 30
    const val DOWNLOAD_AGE_DAYS = 90
    const val DOWNLOAD_MIN_BYTES = 10L * 1024 * 1024
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun isScreenshot(node: FileNode): Boolean {
        val path = node.id.raw.lowercase()
        return !node.isDirectory && ("/screenshots/" in path || node.name.lowercase().startsWith("screenshot"))
    }

    fun isInDownloads(node: FileNode): Boolean {
        val path = node.id.raw.lowercase()
        return "/download/" in path || "/downloads/" in path
    }

    fun isApk(node: FileNode): Boolean = !node.isDirectory && node.name.lowercase().endsWith(".apk")

    /** Last changed more than [days] ago; an unknown date (0) is never old. */
    fun isOlderThan(
        node: FileNode,
        days: Int,
        now: Long,
    ): Boolean = node.modifiedAt in 1 until now - days * DAY_MILLIS

    fun isOldScreenshot(
        node: FileNode,
        now: Long,
    ): Boolean = isScreenshot(node) && isOlderThan(node, SCREENSHOT_AGE_DAYS, now)

    fun isOldDownload(
        node: FileNode,
        now: Long,
    ): Boolean =
        !node.isDirectory && isInDownloads(node) && node.size >= DOWNLOAD_MIN_BYTES &&
            isOlderThan(node, DOWNLOAD_AGE_DAYS, now)

    /** Cards whose items are safe to remove without a look: they start selected. */
    fun preselected(category: StorageAnalysisCategory): Boolean =
        category == StorageAnalysisCategory.EMPTY_FOLDERS || category == StorageAnalysisCategory.TEMP_AND_CACHE

    /** Why the card suggests its files, shown above the list. */
    fun reason(category: StorageAnalysisCategory): String =
        when (category) {
            StorageAnalysisCategory.LARGE_FILES -> "Files over 50 MB. Check each one; most are videos or backups."
            StorageAnalysisCategory.DUPLICATE_FILES ->
                "Files with identical content. Keep one of each keeps the first copy of every group."
            StorageAnalysisCategory.EMPTY_FOLDERS ->
                "Folders with nothing inside. Safe to remove, so they start selected."
            StorageAnalysisCategory.TEMP_AND_CACHE ->
                "Temporary, log, backup and thumbnail files that apps recreate. They start selected."
            StorageAnalysisCategory.OLD_SCREENSHOTS -> "Screenshots older than $SCREENSHOT_AGE_DAYS days."
            StorageAnalysisCategory.OLD_DOWNLOADS ->
                "Downloads over 10 MB that haven't changed in $DOWNLOAD_AGE_DAYS days."
            StorageAnalysisCategory.INSTALLED_APKS -> "Install files for apps that are already on this phone."
        }
}
