package com.devbehindyou.atomicfilemanager.data.volume

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.GetDirectoryListingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Deliberately not a data class: snapshots hold every file on the phone, and structural
 * `equals` on such a list would run on the main thread whenever Compose compares state.
 */
class PhoneIndexSnapshot(
    val files: List<FileNode> = emptyList(),
    val complete: Boolean = false,
    val unreadable: Int = 0,
)

/**
 * Enumerates readable shared volumes, including files outside Android's media index.
 * Hidden (dot-prefixed) files and folders are skipped.
 */
class PhoneFileIndex(
    private val listDirectory: GetDirectoryListingUseCase,
    /**
     * Files already known from the search index (ALL_IN_ONE_PLAN.md §16.2 H5). When it has any,
     * they are shown at once while the walk confirms them, instead of an empty screen that fills
     * up over the walk. A manual refresh skips them.
     */
    private val known: suspend (List<FileNodeId>) -> List<FileNode> = { emptyList() },
) {
    private class CacheEntry(
        val roots: List<FileNodeId>,
        val snapshot: PhoneIndexSnapshot,
        val createdAtNanos: Long,
    )

    @Volatile
    private var cache: CacheEntry? = null

    /** Drops the cached scan so the next [scan] walks the volumes again. */
    fun invalidate() {
        cache = null
    }

    fun scan(
        roots: List<FileNodeId>,
        refresh: Boolean = false,
    ): Flow<PhoneIndexSnapshot> =
        flow {
            val hit = cache
            val fresh = hit != null && hit.roots == roots && System.nanoTime() - hit.createdAtNanos < CACHE_TTL_NANOS
            if (!refresh && hit != null && fresh) {
                emit(hit.snapshot)
                return@flow
            }
            val seeded = if (refresh) emptyList() else known(roots)
            // A walk's partial results would briefly show fewer files than the seed, so with a seed
            // only the finished walk replaces it.
            if (seeded.isNotEmpty()) emit(PhoneIndexSnapshot(seeded))
            val canonicalRoots = roots.mapNotNull { canonicalPathOf(it) }
            val pending = ArrayDeque(roots)
            val visited = HashSet<String>()
            val found = LinkedHashMap<FileNodeId, FileNode>()
            var unreadable = 0
            var lastUpdate = System.nanoTime()
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val directory = pending.removeFirst()
                val canonical = canonicalPathOf(directory)
                // The canonical path is resolved once per directory. Requiring it to stay inside a
                // selected volume stops links that leave the volume, and `visited` stops link cycles.
                // Comparing canonical roots (not raw paths) keeps volumes reached through a symlink,
                // such as /storage/<uuid> -> /mnt/media_rw/<uuid>, scannable.
                if (canonical == null) {
                    unreadable++
                } else if (isInside(canonical, canonicalRoots) && visited.add(canonical)) {
                    listDirectory(directory).collect { result ->
                        when (result) {
                            is FileResult.Failure -> {
                                unreadable++
                            }
                            is FileResult.Success -> {
                                for (node in result.value) {
                                    // Dot folders hold app caches such as Movies/.thumbnails and
                                    // Fast Obscure output; their contents are not user collections.
                                    if (node.isHidden) {
                                        continue
                                    } else if (node.isDirectory) {
                                        pending.add(node.id)
                                    } else if (node.access.readable) {
                                        found[node.id] = node
                                    }
                                }
                            }
                        }
                    }
                }
                if (seeded.isEmpty() && System.nanoTime() - lastUpdate > PARTIAL_EMIT_INTERVAL_NANOS) {
                    emit(PhoneIndexSnapshot(found.values.toList(), unreadable = unreadable))
                    lastUpdate = System.nanoTime()
                }
            }
            val result = PhoneIndexSnapshot(found.values.toList(), complete = true, unreadable = unreadable)
            cache = CacheEntry(roots.toList(), result, System.nanoTime())
            emit(result)
        }.flowOn(Dispatchers.IO)

    private fun canonicalPathOf(id: FileNodeId): String? =
        runCatching { File(id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme)).canonicalPath }.getOrNull()

    private fun isInside(
        canonical: String,
        canonicalRoots: List<String>,
    ): Boolean =
        canonicalRoots.any { root ->
            canonical == root || canonical.startsWith(root.trimEnd(File.separatorChar) + File.separatorChar)
        }

    private companion object {
        const val PARTIAL_EMIT_INTERVAL_NANOS = 250_000_000L

        /** Scans are cheap to serve from memory but go stale as files change elsewhere in the app. */
        const val CACHE_TTL_NANOS = 120_000_000_000L
    }
}
