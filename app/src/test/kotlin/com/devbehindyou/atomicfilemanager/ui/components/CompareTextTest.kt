package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.usecase.ChecksumVerdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompareTextTest {
    private fun node(name: String) =
        FileNode(
            id = FileNodeId.file("/d/$name"),
            name = name,
            displayName = name,
            mimeType = null,
            size = 2_048,
            modifiedAt = 0,
            isDirectory = false,
            isHidden = false,
            parentId = null,
            storageType = StorageType.INTERNAL_SHARED,
            access = AccessFlags.FULL,
            childCount = null,
            extras = null,
        )

    @Test
    fun `compare wording says why`() {
        assertEquals("Same content", CompareText.headline(true))
        assertTrue(CompareText.body(true, node("a"), node("b"), hashed = true).contains("identical byte for byte"))
        assertTrue(CompareText.body(false, node("a"), node("b"), hashed = false).contains("differ in size"))
        assertTrue(CompareText.body(false, node("a"), node("b"), hashed = true).contains("contents differ"))
        assertEquals(listOf("Name", "Size"), CompareText.facts(node("a"), null).map { it.label })
    }

    @Test
    fun `checksum verdicts read plainly`() {
        assertTrue(verdictText(ChecksumVerdict.Match("SHA-256")).startsWith("Matches (SHA-256)"))
        assertTrue(verdictText(ChecksumVerdict.Mismatch("MD5")).startsWith("Doesn't match (MD5)"))
        assertTrue(verdictText(ChecksumVerdict.Unsupported("SHA-1")).contains("SHA-1"))
    }
}
