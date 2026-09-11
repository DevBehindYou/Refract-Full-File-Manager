package com.devbehindyou.refract.data.backend.network

import com.devbehindyou.refract.domain.model.AccessFlags
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.StorageType

internal object NetworkNodeHelper {
    fun createNode(
        id: FileNodeId,
        parentId: FileNodeId?,
        name: String,
        size: Long = 0L,
        modifiedAt: Long = System.currentTimeMillis(),
        isDirectory: Boolean = false,
        mimeType: String? = null,
        isVirtual: Boolean = false,
    ): FileNode {
        return FileNode(
            id = id,
            name = name,
            displayName = name,
            mimeType = mimeType,
            size = if (isDirectory && size <= 0L) -1L else size.coerceAtLeast(0L),
            modifiedAt = modifiedAt.coerceAtLeast(0L),
            isDirectory = isDirectory,
            isHidden = name.startsWith("."),
            parentId = parentId,
            storageType = if (isVirtual) StorageType.VIRTUAL else StorageType.INTERNAL_SHARED,
            access = AccessFlags.FULL,
            childCount = null,
            extras = null,
        )
    }
}
