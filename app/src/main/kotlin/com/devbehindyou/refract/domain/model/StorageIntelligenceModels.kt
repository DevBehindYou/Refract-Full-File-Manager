package com.devbehindyou.refract.domain.model

/**
 * Model representing a group of identical files sharing the exact same size and SHA-256 hash.
 */
data class DuplicateGroup(
    val sizeBytes: Long,
    val sha256: String,
    val items: List<FileNode>,
) {
    /**
     * Potential bytes that can be freed by keeping only 1 copy of the duplicates.
     */
    val potentialSavingsBytes: Long
        get() = if (items.size > 1) (items.size - 1) * sizeBytes else 0L
}

/**
 * Categorization of storage analysis targets.
 */
enum class StorageAnalysisCategory(val displayName: String) {
    LARGE_FILES("Large Files"),
    DUPLICATE_FILES("Duplicates"),
    EMPTY_FOLDERS("Empty Folders"),
    TEMP_AND_CACHE("Temp & Cache"),
}

/**
 * Live progress state during an ongoing storage scan.
 */
data class StorageAnalysisProgress(
    val category: StorageAnalysisCategory,
    val scannedFilesCount: Int,
    val foundCount: Int,
    val reclaimedBytesEstimate: Long,
    val isComplete: Boolean,
    val result: StorageAnalysisResult? = null,
)

/**
 * Aggregated scan results for all storage intelligence categories.
 */
data class StorageAnalysisResult(
    val largeFiles: List<FileNode> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val emptyFolders: List<FileNode> = emptyList(),
    val tempCacheFiles: List<FileNode> = emptyList(),
    val scannedFilesCount: Int = 0,
    val isPartial: Boolean = false,
) {
    val totalPotentialSavingsBytes: Long
        get() {
            val temporaryIds = tempCacheFiles.map { it.id }.toSet()
            val duplicateSavings =
                duplicateGroups.sumOf { group ->
                    val nonTemporary = group.items.count { it.id !in temporaryIds }
                    (nonTemporary - 1).coerceAtLeast(0) * group.sizeBytes
                }
            return duplicateSavings + tempCacheFiles.distinctBy { it.id }.sumOf { it.size.coerceAtLeast(0) }
        }
}
