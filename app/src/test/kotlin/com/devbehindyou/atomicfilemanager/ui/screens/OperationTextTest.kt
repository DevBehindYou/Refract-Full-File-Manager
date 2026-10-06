package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.data.database.room.OperationJournalState
import com.devbehindyou.atomicfilemanager.data.database.room.toJournalEntry
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OperationTextTest {
    private val copy =
        FileOperation(
            id = OperationId("op"),
            type = OperationType.COPY,
            sources = listOf(FileNodeId.file("/storage/emulated/0/DCIM/Camera/a.jpg"), FileNodeId.file("/x/b.jpg")),
            destination = FileNodeId.file("/storage/1A2B-3C4D/Backup"),
            options = OperationOptions(),
            createdAt = 0L,
        )

    @Test
    fun `route shows the first source folder and the destination`() {
        assertEquals("/storage/emulated/0/DCIM/Camera → /storage/1A2B-3C4D/Backup", OperationText.route(copy))
    }

    @Test
    fun `history wording follows the journal state`() {
        assertEquals(
            "Copy 2 items · interrupted",
            OperationText.historyTitle(copy.toJournalEntry(OperationJournalState.INTERRUPTED)),
        )
        assertEquals(
            "Copy 2 items · done",
            OperationText.historyTitle(copy.toJournalEntry(OperationJournalState.COMPLETED)),
        )
    }

    @Test
    fun `only unfinished or failed work offers an action`() {
        assertEquals("Resume", OperationText.action(OperationJournalState.INTERRUPTED))
        assertEquals("Retry", OperationText.action(OperationJournalState.PARTIAL))
        assertNull(OperationText.action(OperationJournalState.COMPLETED))
        assertNull(OperationText.action(OperationJournalState.CANCELLED))
    }

    @Test
    fun `a waiting operation says it needs a decision`() {
        assertEquals("Copy 2 items · waiting for your decision", OperationText.waitingTitle(copy))
    }
}
