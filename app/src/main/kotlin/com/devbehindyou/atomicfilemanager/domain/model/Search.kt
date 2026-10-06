package com.devbehindyou.atomicfilemanager.domain.model

/** One indexed file or folder (ALL_IN_ONE_PLAN.md 1.4). */
data class SearchHit(
    val id: FileNodeId,
    val name: String,
    val parentId: FileNodeId?,
    val isDirectory: Boolean,
    /** Bytes; -1 for folders and unknown sizes. */
    val size: Long,
    val modifiedAt: Long,
    val mimeType: String?,
)

/** What the search index knows right now. */
data class SearchIndexState(
    /** Rows in the index (from the last finished build, plus rows of a build in progress). */
    val indexedCount: Int = 0,
    val building: Boolean = false,
    /** When the last build finished; null when there has never been one. */
    val builtAt: Long? = null,
    /** Folders that could not be read in the last build. */
    val unreadable: Int = 0,
)

/** The type chips on the Search screen. */
enum class SearchFilter(val title: String) {
    ALL("All"),
    FOLDERS("Folders"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Docs"),
    ARCHIVES("Archives"),
    APKS("APKs"),
    ;

    fun matches(hit: SearchHit): Boolean =
        when (this) {
            ALL -> true
            FOLDERS -> hit.isDirectory
            IMAGES -> collectionOf(hit) == FileCollection.IMAGES
            VIDEOS -> collectionOf(hit) == FileCollection.VIDEOS
            AUDIO -> collectionOf(hit) == FileCollection.AUDIO
            DOCUMENTS -> !hit.isDirectory && FileCollection.DOCUMENTS.matches(hit.toNode())
            ARCHIVES -> collectionOf(hit) == FileCollection.ARCHIVES
            APKS -> collectionOf(hit) == FileCollection.APKS
        }

    private fun collectionOf(hit: SearchHit): FileCollection? =
        if (hit.isDirectory) {
            null
        } else {
            FileCollection.entries.firstOrNull {
                it != FileCollection.DOCUMENTS && it.matches(hit.toNode())
            }
        }
}

/** The hit as a [FileNode], for previews and for code that classifies nodes. */
fun SearchHit.toNode(): FileNode =
    FileNode(
        id = id,
        name = name,
        displayName = name,
        mimeType = mimeType,
        size = size.coerceAtLeast(-1),
        modifiedAt = modifiedAt.coerceAtLeast(0),
        isDirectory = isDirectory,
        isHidden = name.startsWith('.'),
        parentId = parentId,
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.READ_ONLY,
        childCount = null,
        extras = null,
    )

/**
 * The text of a search box as a SQL LIKE pattern: case-insensitive substring, with `%`, `_` and
 * the escape character itself matched literally. Blank input gives null (no search).
 */
fun likePatternFor(query: String): String? {
    val trimmed = query.trim().lowercase()
    if (trimmed.isEmpty()) return null
    val escaped =
        buildString {
            trimmed.forEach {
                if (it == '\\' || it == '%' || it == '_') append('\\')
                append(it)
            }
        }
    return "%$escaped%"
}
