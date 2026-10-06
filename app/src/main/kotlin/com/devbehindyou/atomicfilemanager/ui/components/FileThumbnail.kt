package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconTile
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSize
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import java.io.File

/**
 * The row's icon tile, with a thumbnail drawn over it for local images and videos (hotspot H7).
 * The tile stays underneath, so while loading, or when a file can't be decoded, the row still
 * shows its type icon. Decoded at the tile's pixel size, never full size; Coil caches and cancels
 * requests for rows that scroll away.
 */
@Composable
fun FileThumbnail(
    node: FileNode,
    modifier: Modifier = Modifier,
) {
    val path = thumbnailPathOf(node)
    Box(modifier) {
        AtomicIconTile(iconFor(node))
        if (path != null) {
            val context = LocalContext.current
            val px = with(LocalDensity.current) { AtomicSize.iconTile.roundToPx() }
            val request =
                remember(path, node.modifiedAt, px) {
                    ImageRequest
                        .Builder(context)
                        .data(File(path))
                        .size(px)
                        // A changed file gets a new cache entry instead of a stale thumbnail.
                        .memoryCacheKey("$path:${node.modifiedAt}:$px")
                        .diskCacheKey("$path:${node.modifiedAt}")
                        .build()
                }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(AtomicSize.iconTile).clip(AtomicShape.sm),
            )
        }
    }
}

/** The local path to thumbnail, or null for folders, other types and non-local storage. */
internal fun thumbnailPathOf(node: FileNode): String? {
    if (node.isDirectory || node.id.prefix != FileNodeId.Prefix.FILE) return null
    val mime = node.mimeType.orEmpty()
    val ext = node.name.substringAfterLast('.', "").lowercase()
    val visual = mime.startsWith("image/") || mime.startsWith("video/") || ext in THUMBNAIL_EXTENSIONS
    return if (visual) node.id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme) else null
}

private val THUMBNAIL_EXTENSIONS =
    setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "mp4", "mkv", "webm", "mov", "3gp", "m4v")
