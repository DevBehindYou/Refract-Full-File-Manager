package com.devbehindyou.atomicfilemanager.domain.model

/** A file or folder the user starred (FR-6.4). Shown on Home; a missing target is shown, not dropped. */
data class Favourite(
    val id: FileNodeId,
    val name: String,
    val isDirectory: Boolean,
    val addedAt: Long,
)

/** Where a recent item came from (ALL_IN_ONE_PLAN.md 1.2). */
enum class RecentSource {
    /** Opened in Atomic File Manager. */
    OPENED,

    /** Recently added or changed media, from MediaStore. */
    MEDIA,
}

data class RecentItem(
    val id: FileNodeId,
    val name: String,
    val mimeType: String?,
    /** Bytes; -1 when unknown. */
    val size: Long,
    /** When it was opened (OPENED) or last modified (MEDIA). */
    val at: Long,
    val source: RecentSource,
)

/** Newest first, each file once (an opened photo is not listed again as new media), at most [limit]. */
fun mergeRecents(
    opened: List<RecentItem>,
    media: List<RecentItem>,
    limit: Int,
): List<RecentItem> =
    (opened + media)
        .sortedByDescending { it.at }
        .distinctBy { it.id }
        .take(limit)
