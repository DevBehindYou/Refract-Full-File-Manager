package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.DuplicateGroup
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisCategory
import com.devbehindyou.atomicfilemanager.domain.model.StorageAnalysisResult
import com.devbehindyou.atomicfilemanager.domain.usecase.CleanupRules

/** Pure selection rules for the storage analysis screen, kept apart so they can be unit-tested. */
internal object StorageCleanupSelection {
    /** Every node a category lists, in display order. */
    fun nodesIn(
        result: StorageAnalysisResult,
        category: StorageAnalysisCategory,
    ): List<FileNode> =
        when (category) {
            StorageAnalysisCategory.LARGE_FILES -> result.largeFiles
            StorageAnalysisCategory.DUPLICATE_FILES -> result.duplicateGroups.flatMap { it.items }
            StorageAnalysisCategory.EMPTY_FOLDERS -> result.emptyFolders
            StorageAnalysisCategory.TEMP_AND_CACHE -> result.tempCacheFiles
            StorageAnalysisCategory.OLD_SCREENSHOTS -> result.oldScreenshots
            StorageAnalysisCategory.OLD_DOWNLOADS -> result.oldDownloads
            StorageAnalysisCategory.INSTALLED_APKS -> result.installedApks
        }

    fun count(
        result: StorageAnalysisResult,
        category: StorageAnalysisCategory,
    ): Int =
        when (category) {
            StorageAnalysisCategory.DUPLICATE_FILES -> result.duplicateGroups.size
            else -> nodesIn(result, category).size
        }

    /** What a card starts with selected: everything on a safe card, nothing elsewhere. */
    fun initialSelection(
        result: StorageAnalysisResult,
        category: StorageAnalysisCategory,
    ): Set<String> =
        if (CleanupRules.preselected(category)) nodesIn(result, category).map { it.id.raw }.toSet() else emptySet()

    /** All copies but the first of each group: the first is kept. */
    fun allButFirstCopy(groups: List<DuplicateGroup>): Set<String> =
        groups.flatMap { group -> group.items.drop(1).map { it.id.raw } }.toSet()

    /** Selects everything in [nodes], or clears them when all are already selected. */
    fun toggleAll(
        selected: Set<String>,
        nodes: List<FileNode>,
    ): Set<String> {
        val ids = nodes.map { it.id.raw }.toSet()
        return if (ids.isNotEmpty() && selected.containsAll(ids)) selected - ids else selected + ids
    }

    /** The selected nodes of [category], resolved from their raw ids. */
    fun selectedNodes(
        result: StorageAnalysisResult,
        category: StorageAnalysisCategory,
        selected: Set<String>,
    ): List<FileNode> = nodesIn(result, category).filter { it.id.raw in selected }.distinctBy { it.id.raw }

    /** Bytes freed by deleting [nodes]; folders and unknown sizes count as zero. */
    fun bytes(nodes: List<FileNode>): Long = nodes.filter { !it.isDirectory }.sumOf { it.size.coerceAtLeast(0L) }
}
