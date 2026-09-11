package com.devbehindyou.refract.domain.repository

import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.HiddenItem
import kotlinx.coroutines.flow.StateFlow

interface HiddenFilesRepository {
    val hiddenItems: StateFlow<List<HiddenItem>>

    suspend fun hideFromGallery(node: FileNode): Result<HiddenItem>
    suspend fun unhideFromGallery(item: HiddenItem): Result<Unit>

    suspend fun fastObscure(node: FileNode): Result<HiddenItem>
    suspend fun restoreFastObscured(item: HiddenItem): Result<Unit>

    suspend fun moveToPrivateStorage(node: FileNode): Result<HiddenItem>
    suspend fun restoreFromPrivateStorage(item: HiddenItem, destinationParent: FileNodeId): Result<Unit>

    suspend fun deleteHiddenItem(item: HiddenItem): Result<Unit>
    suspend fun recoverUnfinishedOperations()
}
