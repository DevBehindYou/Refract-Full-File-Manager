package com.devbehindyou.atomicfilemanager.data.search

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationType

/**
 * Local folders whose listing [operation] may have changed: the folders its sources were in, and
 * its destination folder. Restores from the Trash are not covered (the original folders are only
 * in the trash records); they show up after the next full build.
 */
fun foldersTouchedBy(operation: FileOperation): Set<FileNodeId> {
    val folders = mutableSetOf<FileNodeId>()
    if (operation.type != OperationType.RESTORE_FROM_TRASH) {
        operation.sources.mapNotNullTo(folders) { parentOf(it) }
    }
    // RENAME keeps the new name in `destination`, not a folder.
    if (operation.type != OperationType.RENAME) {
        operation.destination?.takeIf { it.prefix == FileNodeId.Prefix.FILE }?.let(folders::add)
    }
    return folders
}

private fun parentOf(id: FileNodeId): FileNodeId? {
    if (id.prefix != FileNodeId.Prefix.FILE) return null
    val path = id.raw.removePrefix(FileNodeId.Prefix.FILE.scheme).trimEnd('/')
    val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
    return if (parent.isEmpty()) null else FileNodeId.file(parent)
}
