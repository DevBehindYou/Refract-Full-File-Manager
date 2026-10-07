package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.CollisionPolicy
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** How one path differs between the two folders (ALL_IN_ONE_PLAN.md 4.1). */
enum class CompareState {
    ONLY_LEFT,
    ONLY_RIGHT,
    NEWER_LEFT,
    NEWER_RIGHT,

    /** Same modified time (within tolerance) but a different size, or a file on one side and a folder on the other. */
    DIFFERENT,
    SAME,
}

/**
 * One path in a comparison. [path] is relative to the compared folders, "/"-separated.
 * [leftParent] and [rightParent] are the folders that hold it on each side, which always exist:
 * the walk only goes into folders present on both sides.
 */
data class CompareEntry(
    val path: String,
    val left: FileNode?,
    val right: FileNode?,
    val leftParent: FileNodeId,
    val rightParent: FileNodeId,
    val state: CompareState,
)

enum class SyncMode {
    /** Copies what is missing or newer on the left into the right. Never deletes. */
    COPY_NEW,

    /** Makes the right match the left: copies as above, replaces differing files, and trashes extras. */
    MIRROR,
}

/** What a sync will do; always shown to the user before it runs. */
data class SyncPlan(
    val copies: List<CompareEntry>,
    val replacements: List<CompareEntry>,
    val trash: List<FileNode>,
) {
    val isEmpty: Boolean get() = copies.isEmpty() && replacements.isEmpty() && trash.isEmpty()
}

/** Pure planning rules for [FolderComparer] results; unit-tested. */
object SyncPlanner {
    fun plan(
        entries: List<CompareEntry>,
        mode: SyncMode,
    ): SyncPlan {
        val copies = entries.filter { it.state == CompareState.ONLY_LEFT }
        val replaceStates =
            when (mode) {
                SyncMode.COPY_NEW -> setOf(CompareState.NEWER_LEFT)
                SyncMode.MIRROR -> setOf(CompareState.NEWER_LEFT, CompareState.NEWER_RIGHT, CompareState.DIFFERENT)
            }
        // A file on one side and a folder on the other can't be replaced in one step; it is left alone.
        val replacements =
            entries.filter {
                it.state in replaceStates && it.left?.isDirectory == false && it.right?.isDirectory == false
            }
        val trash =
            if (mode == SyncMode.MIRROR) {
                entries.filter { it.state == CompareState.ONLY_RIGHT }.mapNotNull { it.right }
            } else {
                emptyList()
            }
        return SyncPlan(copies, replacements, trash)
    }

    /**
     * The plan as queue operations: one copy per destination folder (new files keep both on a
     * surprise clash, replacements overwrite), then one move to Trash. Deletes always use Trash.
     */
    fun operations(
        plan: SyncPlan,
        now: Long,
        newId: () -> OperationId = OperationId::random,
    ): List<FileOperation> {
        fun copy(
            entries: List<CompareEntry>,
            policy: CollisionPolicy,
        ): List<FileOperation> =
            entries.groupBy { it.rightParent }.map { (destination, group) ->
                FileOperation(
                    id = newId(),
                    type = OperationType.COPY,
                    sources = group.mapNotNull { it.left?.id },
                    destination = destination,
                    options = OperationOptions(collisionPolicy = policy),
                    createdAt = now,
                )
            }
        val trash =
            if (plan.trash.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    FileOperation(
                        id = newId(),
                        type = OperationType.TRASH,
                        sources = plan.trash.map { it.id },
                        destination = null,
                        options = OperationOptions(useTrash = true),
                        createdAt = now,
                    ),
                )
            }
        return copy(plan.copies, CollisionPolicy.KEEP_BOTH) + copy(plan.replacements, CollisionPolicy.OVERWRITE) + trash
    }

    /** Compares two nodes at the same path. Unknown times or sizes never count as newer or different. */
    fun state(
        left: FileNode,
        right: FileNode,
        toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    ): CompareState {
        if (left.isDirectory != right.isDirectory) return CompareState.DIFFERENT
        if (left.isDirectory) return CompareState.SAME
        val known = left.modifiedAt > 0 && right.modifiedAt > 0
        val delta = left.modifiedAt - right.modifiedAt
        return when {
            known && abs(delta) > toleranceMs -> if (delta > 0) CompareState.NEWER_LEFT else CompareState.NEWER_RIGHT
            left.size >= 0 && right.size >= 0 && left.size != right.size -> CompareState.DIFFERENT
            else -> CompareState.SAME
        }
    }

    /** FAT and many servers store times to 2 s, so closer than that counts as the same time. */
    const val DEFAULT_TOLERANCE_MS = 2_000L
}

/**
 * Walks two folders on any backends and lists every path that exists on either side
 * (ALL_IN_ONE_PLAN.md 4.1). Folders present on both sides are walked into; a folder on one side
 * only is one entry. Nothing is changed.
 */
class FolderComparer(
    private val backendFor: (FileNodeId) -> StorageBackend,
) {
    suspend fun compare(
        left: FileNodeId,
        right: FileNodeId,
        toleranceMs: Long = SyncPlanner.DEFAULT_TOLERANCE_MS,
    ): FileResult<List<CompareEntry>> =
        withContext(Dispatchers.IO) {
            val entries = mutableListOf<CompareEntry>()
            val pending = ArrayDeque(listOf(Triple("", left, right)))
            while (pending.isNotEmpty()) {
                coroutineContext.ensureActive()
                val (prefix, leftFolder, rightFolder) = pending.removeFirst()
                val leftChildren =
                    when (val listed = list(leftFolder)) {
                        is FileResult.Success -> listed.value
                        is FileResult.Failure -> return@withContext listed
                    }
                val rightChildren =
                    when (val listed = list(rightFolder)) {
                        is FileResult.Success -> listed.value
                        is FileResult.Failure -> return@withContext listed
                    }
                (leftChildren.keys + rightChildren.keys).sorted().forEach { name ->
                    val l = leftChildren[name]
                    val r = rightChildren[name]
                    val path = if (prefix.isEmpty()) name else "$prefix/$name"
                    val state =
                        when {
                            r == null -> CompareState.ONLY_LEFT
                            l == null -> CompareState.ONLY_RIGHT
                            else -> SyncPlanner.state(l, r, toleranceMs)
                        }
                    if (l != null && r != null && l.isDirectory && r.isDirectory) {
                        pending.addLast(Triple(path, l.id, r.id))
                    } else {
                        entries += CompareEntry(path, l, r, leftFolder, rightFolder, state)
                    }
                }
            }
            FileResult.Success(entries.sortedBy { it.path })
        }

    private suspend fun list(folder: FileNodeId): FileResult<Map<String, FileNode>> {
        val children = mutableMapOf<String, FileNode>()
        var failure: FileResult.Failure? = null
        backendFor(folder).listChildren(folder).collect { chunk ->
            when (chunk) {
                is FileResult.Success -> chunk.value.forEach { children[it.name] = it }
                is FileResult.Failure -> failure = chunk
            }
        }
        return failure ?: FileResult.Success(children)
    }
}
