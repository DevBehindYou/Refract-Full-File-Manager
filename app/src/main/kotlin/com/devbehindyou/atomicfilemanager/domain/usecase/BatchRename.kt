package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FailedItem
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import java.util.Locale
import java.util.UUID

enum class NameCase { KEEP, LOWER, UPPER, TITLE }

/** Numbers each name in selection order: "Holiday 01", "Holiday 02", … */
data class Numbering(
    val start: Int = 1,
    val digits: Int = 2,
    val separator: String = " ",
)

/**
 * How every selected name changes (ALL_IN_ONE_PLAN.md 2.1). Steps run in this order on the name
 * without its extension: [newBase] replaces it, then find/replace, prefix and suffix, numbering, case.
 * The extension is kept as it was unless [keepExtension] is false.
 */
data class RenamePattern(
    val newBase: String = "",
    val find: String = "",
    val replace: String = "",
    val matchCase: Boolean = false,
    val prefix: String = "",
    val suffix: String = "",
    val numbering: Numbering? = null,
    val case: NameCase = NameCase.KEEP,
    val keepExtension: Boolean = true,
)

enum class RenameProblem { EMPTY, INVALID_CHARACTER, DUPLICATE_IN_BATCH, EXISTS }

data class RenamePreview(
    val node: FileNode,
    val newName: String,
    val problem: RenameProblem?,
) {
    val changes: Boolean get() = newName != node.name
}

/** Pure rules for batch rename; unit-tested. */
object BatchRenameRules {
    private val invalid = Regex("[/\\\\:*?\"<>|\\u0000]")

    fun newName(
        pattern: RenamePattern,
        name: String,
        index: Int,
        isDirectory: Boolean = false,
    ): String {
        val dot = name.lastIndexOf('.')
        val splits = !isDirectory && dot > 0
        var stem = if (splits) name.substring(0, dot) else name
        val extension = if (splits) name.substring(dot) else ""
        if (pattern.newBase.isNotBlank()) stem = pattern.newBase.trim()
        if (pattern.find.isNotEmpty()) {
            stem = stem.replace(pattern.find, pattern.replace, ignoreCase = !pattern.matchCase)
        }
        stem = pattern.prefix + stem + pattern.suffix
        pattern.numbering?.let { n ->
            val number = (n.start + index).toString().padStart(n.digits.coerceIn(1, 9), '0')
            stem = if (stem.isEmpty()) number else stem + n.separator + number
        }
        stem =
            when (pattern.case) {
                NameCase.KEEP -> stem
                NameCase.LOWER -> stem.lowercase(Locale.ROOT)
                NameCase.UPPER -> stem.uppercase(Locale.ROOT)
                NameCase.TITLE ->
                    stem.split(' ').joinToString(" ") { word ->
                        word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                    }
            }
        return stem.trim() + if (pattern.keepExtension) extension else ""
    }

    /**
     * Every node with its new name and what, if anything, stops it. [folderNames] are all names in
     * the folder; names are compared ignoring case, as shared storage does.
     */
    fun preview(
        nodes: List<FileNode>,
        pattern: RenamePattern,
        folderNames: Collection<String>,
    ): List<RenamePreview> {
        val names = nodes.mapIndexed { i, node -> newName(pattern, node.name, i, node.isDirectory) }
        val batchOld = nodes.map { it.name.lowercase(Locale.ROOT) }.toSet()
        val others = folderNames.map { it.lowercase(Locale.ROOT) }.filterNot { it in batchOld }.toSet()
        val counts = names.groupingBy { it.lowercase(Locale.ROOT) }.eachCount()
        return nodes.mapIndexed { i, node ->
            val name = names[i]
            val key = name.lowercase(Locale.ROOT)
            val problem =
                when {
                    name.isBlank() || name == "." || name == ".." -> RenameProblem.EMPTY
                    invalid.containsMatchIn(name) -> RenameProblem.INVALID_CHARACTER
                    (counts[key] ?: 0) > 1 -> RenameProblem.DUPLICATE_IN_BATCH
                    key in others -> RenameProblem.EXISTS
                    else -> null
                }
            RenamePreview(node, name, problem)
        }
    }
}

data class RenamedItem(
    val node: FileNode,
    val previousName: String,
)

data class BatchRenameResult(
    val renamed: List<RenamedItem>,
    val failed: List<FailedItem>,
)

/**
 * Renames several items (ALL_IN_ONE_PLAN.md 2.1). When a new name is taken by another item of the
 * same batch (a swap, or a change of case only), every item first gets a temporary hidden name so no
 * rename ever lands on a name still in use. An item that can't reach its new name is put back.
 * Undo is the same call with [BatchRenameResult.renamed] pointed back at the previous names.
 */
class BatchRenameUseCase(
    private val backendSelector: (FileNodeId) -> StorageBackend,
) {
    suspend operator fun invoke(changes: List<Pair<FileNode, String>>): BatchRenameResult {
        val todo = changes.filter { (node, name) -> node.name != name }
        val oldNames = todo.map { it.first.name.lowercase(Locale.ROOT) }.toSet()
        val needsTemp = todo.any { (_, name) -> name.lowercase(Locale.ROOT) in oldNames }
        val renamed = mutableListOf<RenamedItem>()
        val failed = mutableListOf<FailedItem>()

        // Short and unique per run, so a long name can't grow past the file-name limit.
        val token = UUID.randomUUID().toString().take(TOKEN_LENGTH)
        val staged =
            if (!needsTemp) {
                todo.map { (node, name) -> Staged(node, node.id, name, viaTemp = false) }
            } else {
                todo.mapIndexedNotNull { i, (node, name) ->
                    when (val temp = rename(node.id, "$TEMP_PREFIX$token-$i")) {
                        is FileResult.Success -> Staged(node, temp.value.id, name, viaTemp = true)
                        is FileResult.Failure -> {
                            failed += FailedItem(node.id, node.name, temp.error)
                            null
                        }
                    }
                }
            }
        for (item in staged) {
            when (val result = rename(item.currentId, item.newName)) {
                is FileResult.Success -> renamed += RenamedItem(result.value, item.original.name)
                is FileResult.Failure -> {
                    if (item.viaTemp) rename(item.currentId, item.original.name)
                    failed += FailedItem(item.original.id, item.original.name, result.error)
                }
            }
        }
        return BatchRenameResult(renamed, failed)
    }

    suspend fun undo(result: BatchRenameResult): BatchRenameResult =
        invoke(result.renamed.map { it.node to it.previousName })

    private suspend fun rename(
        id: FileNodeId,
        name: String,
    ): FileResult<FileNode> = backendSelector(id).rename(id, name)

    private class Staged(
        val original: FileNode,
        val currentId: FileNodeId,
        val newName: String,
        val viaTemp: Boolean,
    )

    private companion object {
        const val TEMP_PREFIX = ".atomic-rn-"
        const val TOKEN_LENGTH = 8
    }
}
