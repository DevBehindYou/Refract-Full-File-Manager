package com.devbehindyou.atomicfilemanager.domain.repository

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.SearchHit
import com.devbehindyou.atomicfilemanager.domain.model.SearchIndexState
import kotlinx.coroutines.flow.StateFlow

/** Search across all readable storage from an index, not a live walk (ALL_IN_ONE_PLAN.md 1.4). */
interface SearchIndex {
    val state: StateFlow<SearchIndexState>

    /** Names containing [query] (case-insensitive), folders first, at most [limit]. */
    suspend fun search(
        query: String,
        limit: Int,
    ): List<SearchHit>

    /** Starts a build in the background when the index is empty or older than its freshness window. */
    fun refreshIfStale(roots: List<FileNodeId>)

    /** Starts a full build now, unless one is already running. */
    fun rebuild(roots: List<FileNodeId>)

    /**
     * Re-reads just [folders] (one level each) after the app changed them: new entries are added,
     * missing ones removed with everything below them. Does nothing before the first full build.
     */
    suspend fun refreshFolders(folders: Collection<FileNodeId>)

    /**
     * Every file (not folder) indexed under [roots], from the last finished build; empty before
     * the first one. Lets category screens show results at once instead of waiting for a walk
     * (ALL_IN_ONE_PLAN.md §16.2 H5).
     */
    suspend fun files(roots: List<FileNodeId>): List<SearchHit> = emptyList()
}
