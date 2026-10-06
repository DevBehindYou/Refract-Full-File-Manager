package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FileThumbnailTest {
    private fun node(
        id: FileNodeId,
        name: String,
        mime: String? = null,
        directory: Boolean = false,
    ) = FileNode(
        id = id,
        name = name,
        displayName = name,
        mimeType = mime,
        size = 1,
        modifiedAt = 0,
        isDirectory = directory,
        isHidden = false,
        parentId = null,
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL,
        childCount = null,
        extras = null,
    )

    @Test
    fun `local images and videos get a thumbnail path`() {
        assertEquals("/sdcard/DCIM/a.JPG", thumbnailPathOf(node(FileNodeId.file("/sdcard/DCIM/a.JPG"), "a.JPG")))
        assertEquals("/sdcard/clip", thumbnailPathOf(node(FileNodeId.file("/sdcard/clip"), "clip", mime = "video/mp4")))
    }

    @Test
    fun `folders, other types and remote files keep the icon`() {
        assertNull(thumbnailPathOf(node(FileNodeId.file("/sdcard/DCIM"), "DCIM", directory = true)))
        assertNull(thumbnailPathOf(node(FileNodeId.file("/sdcard/notes.txt"), "notes.txt", mime = "text/plain")))
        assertNull(thumbnailPathOf(node(FileNodeId.ftp("srv", "/a.jpg"), "a.jpg", mime = "image/jpeg")))
    }
}
