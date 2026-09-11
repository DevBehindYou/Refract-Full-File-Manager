package com.devbehindyou.refract.domain.repository

import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.TransferBubble
import kotlinx.coroutines.flow.StateFlow

interface TransferBubbleRepository {
    val bubbles: StateFlow<List<TransferBubble>>

    suspend fun createBubble(name: String? = null): Result<TransferBubble>
    suspend fun addItemsToBubble(bubbleId: String, items: List<FileNode>): Result<Unit>
    suspend fun removeItemFromBubble(bubbleId: String, itemId: Long): Result<Unit>
    suspend fun clearBubble(bubbleId: String): Result<Unit>
    suspend fun deleteBubble(bubbleId: String): Result<Unit>
    suspend fun getBubble(bubbleId: String): TransferBubble?
}
