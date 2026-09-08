package com.devbehindyou.refract.domain.model

/**
 * A file, folder, or document, wherever it physically lives
 * (`architecture/STORAGE_ARCHITECTURE.md` §1). No code above the data layer ever handles a
 * `File`, a `Uri`, or a `Cursor` — everything is a [FileNode].
 *
 * Deliberately **not** annotated `@Immutable`: that's `androidx.compose.runtime.Immutable`,
 * and this phase's own acceptance criterion is zero Android imports in `domain`, lint-enforced
 * by `NoAndroidInDomain`. The architecture doc's example carries the annotation; that specific
 * detail isn't followed here for that reason. If a UI-layer stability hint is wanted later,
 * it belongs on a `core.ui`-side projection, not this type.
 */
data class FileNode(
    val id: FileNodeId,
    val name: String,
    val displayName: String,
    val mimeType: String?,
    /** Bytes; `-1` when unknown (an uncomputed folder — never computed inline, rule §7). */
    val size: Long,
    /** Epoch millis; `0` when unknown. */
    val modifiedAt: Long,
    val isDirectory: Boolean,
    val isHidden: Boolean,
    /** `null` at a volume root. */
    val parentId: FileNodeId?,
    val storageType: StorageType,
    val access: AccessFlags,
    /** `null` when not yet counted. */
    val childCount: Int?,
    /** Lazily-attached: duration, dimensions, apk package, ... */
    val extras: NodeExtras?,
) {
    init {
        require(size >= -1) { "size must be -1 (unknown) or >= 0, was $size" }
        require(modifiedAt >= 0) { "modifiedAt must be >= 0 (0 = unknown), was $modifiedAt" }
        require(childCount == null || childCount >= 0) {
            "childCount must be null or >= 0, was $childCount"
        }
    }
}

enum class StorageType { INTERNAL_PRIVATE, INTERNAL_SHARED, SD_CARD, USB, MEDIA_INDEX, VIRTUAL }

/**
 * Not decoration (`architecture/STORAGE_ARCHITECTURE.md` §1): on API 30+ a node can be
 * readable but not writable, or listed but not openable. The UI disables actions based on
 * these flags rather than letting them fail — backends must populate them honestly.
 */
data class AccessFlags(
    val readable: Boolean,
    val writable: Boolean,
    val deletable: Boolean,
    val renamable: Boolean,
) {
    companion object {
        val NONE = AccessFlags(readable = false, writable = false, deletable = false, renamable = false)
        val FULL = AccessFlags(readable = true, writable = true, deletable = true, renamable = true)
        val READ_ONLY = AccessFlags(readable = true, writable = false, deletable = false, renamable = false)
    }
}

/**
 * Lazily-attached metadata, only ever present once resolved. The doc names three examples
 * ("duration, dimensions, apk package") without a full schema — this shape is this file's
 * own reasonable extension of those three examples, not a literal spec.
 */
sealed interface NodeExtras {
    data class Image(val widthPx: Int, val heightPx: Int) : NodeExtras
    data class Video(val widthPx: Int, val heightPx: Int, val durationMillis: Long) : NodeExtras
    data class Audio(val durationMillis: Long) : NodeExtras
    data class Apk(val packageName: String, val versionName: String?, val versionCode: Long) : NodeExtras
}
