package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

/**
 * Selection power in Files (ALL_IN_ONE_PLAN.md 1.3). Pure, over the list as it is shown, so the
 * rules are unit-tested without Compose.
 */
internal object SelectionRules {
    /**
     * Long-press with a selection already open selects everything between the last item the user
     * touched ([anchor]) and [target], inclusive, keeping what was selected before. Without a usable
     * anchor it toggles [target] like a tap.
     */
    fun longPress(
        shown: List<FileNode>,
        selected: Set<FileNodeId>,
        anchor: FileNodeId?,
        target: FileNodeId,
    ): Set<FileNodeId> {
        val from = shown.indexOfFirst { it.id == anchor }
        val to = shown.indexOfFirst { it.id == target }
        if (selected.isEmpty() || from < 0 || to < 0 || from == to) return toggle(selected, target)
        return selected + shown.subList(minOf(from, to), maxOf(from, to) + 1).map { it.id }
    }

    fun toggle(
        selected: Set<FileNodeId>,
        id: FileNodeId,
    ): Set<FileNodeId> = if (id in selected) selected - id else selected + id

    /** Everything shown that isn't selected, and nothing that is. */
    fun invert(
        shown: List<FileNode>,
        selected: Set<FileNodeId>,
    ): Set<FileNodeId> = shown.map { it.id }.filterNot { it in selected }.toSet()

    /** Adds every shown item of the same kind as the selected ones: folders, or files with the same extension. */
    fun sameType(
        shown: List<FileNode>,
        selected: Set<FileNodeId>,
    ): Set<FileNodeId> {
        val kinds = shown.filter { it.id in selected }.map(::kindOf).toSet()
        return selected + shown.filter { kindOf(it) in kinds }.map { it.id }
    }

    /** "folder", the lower-case extension, or the MIME type for files without one. */
    fun kindOf(node: FileNode): String {
        if (node.isDirectory) return "folder"
        val dot = node.name.lastIndexOf('.')
        return if (dot > 0 && dot < node.name.length - 1) {
            node.name.substring(dot + 1).lowercase()
        } else {
            "mime:${node.mimeType.orEmpty()}"
        }
    }
}
