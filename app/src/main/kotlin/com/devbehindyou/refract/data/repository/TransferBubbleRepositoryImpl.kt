package com.devbehindyou.refract.data.repository

import com.devbehindyou.refract.data.database.TransferBubbleDatabaseHelper
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.TransferBubble
import com.devbehindyou.refract.domain.repository.TransferBubbleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class TransferBubbleRepositoryImpl(
    private val dbHelper: TransferBubbleDatabaseHelper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransferBubbleRepository {
    private val _bubbles = MutableStateFlow<List<TransferBubble>>(emptyList())
    override val bubbles: StateFlow<List<TransferBubble>> = _bubbles.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        _bubbles.value = dbHelper.getAllBubbles()
    }

    override suspend fun createBubble(name: String?): Result<TransferBubble> =
        withContext(ioDispatcher) {
            val currentCount = dbHelper.countBubbles()
            if (currentCount >= TransferBubble.MAX_BUBBLES) {
                return@withContext Result.failure(
                    IllegalStateException("Maximum of ${TransferBubble.MAX_BUBBLES} transfer bubbles allowed"),
                )
            }

            val bubbleNumber = currentCount + 1
            val displayName = name ?: "Bubble $bubbleNumber"
            val newBubble =
                TransferBubble(
                    id = UUID.randomUUID().toString(),
                    displayName = displayName,
                    sortOrder = currentCount,
                )

            val inserted = dbHelper.insertBubble(newBubble)
            if (inserted) {
                refresh()
                Result.success(newBubble)
            } else {
                Result.failure(IllegalStateException("Failed to persist transfer bubble"))
            }
        }

    override suspend fun addItemsToBubble(
        bubbleId: String,
        items: List<FileNode>,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            if (items.isEmpty()) return@withContext Result.success(Unit)
            dbHelper.addItems(bubbleId, items)
            refresh()
            Result.success(Unit)
        }

    override suspend fun removeItemFromBubble(
        bubbleId: String,
        itemId: Long,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            dbHelper.removeItem(itemId)
            refresh()
            Result.success(Unit)
        }

    override suspend fun clearBubble(bubbleId: String): Result<Unit> =
        withContext(ioDispatcher) {
            dbHelper.clearBubble(bubbleId)
            refresh()
            Result.success(Unit)
        }

    override suspend fun deleteBubble(bubbleId: String): Result<Unit> =
        withContext(ioDispatcher) {
            dbHelper.deleteBubble(bubbleId)
            refresh()
            Result.success(Unit)
        }

    override suspend fun getBubble(bubbleId: String): TransferBubble? =
        withContext(ioDispatcher) {
            _bubbles.value.find { it.id == bubbleId }
        }
}
