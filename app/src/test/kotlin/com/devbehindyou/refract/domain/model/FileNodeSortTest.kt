package com.devbehindyou.refract.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileNodeSortTest {

    private fun node(
        name: String,
        size: Long = 0,
        modifiedAt: Long = 1L,
        isDirectory: Boolean = false,
        mimeType: String? = "text/plain",
    ) = FileNode(
        id = FileNodeId.file("/root/$name"),
        name = name,
        displayName = name,
        mimeType = mimeType,
        size = size,
        modifiedAt = modifiedAt,
        isDirectory = isDirectory,
        isHidden = false,
        parentId = FileNodeId.file("/root"),
        storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL,
        childCount = if (isDirectory) 0 else null,
        extras = null,
    )

    @Test
    fun `natural order sorts file2 before file10`() {
        val names = listOf("file10.txt", "file2.txt", "file1.txt")
            .map(::node)
            .sortedWith(compareBy(NaturalOrderComparator.ROOT) { it.name })
            .map { it.name }

        assertEquals(listOf("file1.txt", "file2.txt", "file10.txt"), names)
    }

    @Test
    fun `NaturalOrderComparator directly sorts mixed-length numeric runs correctly`() {
        val sorted = listOf("img9", "img10", "img2", "img1").sortedWith(NaturalOrderComparator.ROOT)

        assertEquals(listOf("img1", "img2", "img9", "img10"), sorted)
    }

    @Test
    fun `NaturalOrderComparator is case-insensitive on alphabetic runs`() {
        val sorted = listOf("Banana", "apple", "Cherry").sortedWith(NaturalOrderComparator.ROOT)

        assertEquals(listOf("apple", "Banana", "Cherry"), sorted)
    }

    @Test
    fun `leading zeros do not affect numeric ordering (file007 equals file7 numerically)`() {
        // Not a general string-equality claim — "file007" and "file7" are genuinely
        // different names — just that NEITHER numerically precedes the other, which is
        // the correct natural-sort behaviour real natsort implementations use.
        val cmp = NaturalOrderComparator.ROOT.compare("file007.txt", "file7.txt")

        assertEquals(0, cmp)
    }

    @Test
    fun `a longer digit run without leading zeros still sorts after a shorter one`() {
        val sorted = listOf("v009", "v10", "v2").sortedWith(NaturalOrderComparator.ROOT)

        // v009 == v9 numerically, and 9 < 10, so v009 sorts before v10; v2 sorts before both.
        assertEquals(listOf("v2", "v009", "v10"), sorted)
    }

    @Test
    fun `accented characters sort near their unaccented equivalent under the ROOT locale`() {
        // Collator.SECONDARY strength (case-insensitive, accent-sensitive) still places
        // accented and unaccented forms of the same base letter adjacently rather than at
        // an unrelated codepoint-based position (a plain Char comparison would put "é"
        // after "z", since its codepoint is far beyond the ASCII alphabet).
        val sorted = listOf("epsilon", "café", "cafe", "delta").sortedWith(NaturalOrderComparator.ROOT)
        val cafeIndex = sorted.indexOf("cafe")
        val cafeAccentedIndex = sorted.indexOf("café")

        assertTrue(kotlin.math.abs(cafeIndex - cafeAccentedIndex) == 1)
    }

    @Test
    fun `folders sort before files regardless of name when foldersFirst is true`() {
        val spec = SortSpec(field = SortField.NAME, ascending = true, foldersFirst = true)
        val nodes = listOf(
            node("z-file.txt"),
            node("a-folder", isDirectory = true),
        )

        val sorted = nodes.sortedWith(spec.comparator())

        assertEquals(listOf("a-folder", "z-file.txt"), sorted.map { it.name })
    }

    @Test
    fun `descending name sort reverses within the folders-first grouping, not the grouping itself`() {
        val spec = SortSpec(field = SortField.NAME, ascending = false, foldersFirst = true)
        val nodes = listOf(
            node("b-file.txt"),
            node("a-file.txt"),
            node("z-folder", isDirectory = true),
            node("m-folder", isDirectory = true),
        )

        val sorted = nodes.sortedWith(spec.comparator()).map { it.name }

        assertEquals(listOf("z-folder", "m-folder", "b-file.txt", "a-file.txt"), sorted)
    }

    @Test
    fun `size sort orders smallest to largest ascending`() {
        val spec = SortSpec(field = SortField.SIZE, ascending = true, foldersFirst = false)
        val nodes = listOf(node("big", size = 300), node("small", size = 10), node("mid", size = 100))

        val sorted = nodes.sortedWith(spec.comparator()).map { it.name }

        assertEquals(listOf("small", "mid", "big"), sorted)
    }

    @Test
    fun `date sort orders oldest to newest ascending`() {
        val spec = SortSpec(field = SortField.DATE, ascending = true, foldersFirst = false)
        val nodes = listOf(
            node("newest", modifiedAt = 300),
            node("oldest", modifiedAt = 10),
            node("middle", modifiedAt = 100),
        )

        val sorted = nodes.sortedWith(spec.comparator()).map { it.name }

        assertEquals(listOf("oldest", "middle", "newest"), sorted)
    }

    @Test
    fun `type sort groups by mime type, then falls back to natural name order within each group`() {
        val spec = SortSpec(field = SortField.TYPE, ascending = true, foldersFirst = false)
        val nodes = listOf(
            node("z-doc.pdf", mimeType = "application/pdf"),
            node("b-image.png", mimeType = "image/png"),
            node("a-doc.pdf", mimeType = "application/pdf"),
            node("a-image.png", mimeType = "image/png"),
        )

        val sorted = nodes.sortedWith(spec.comparator()).map { it.name }

        // "application/pdf" < "image/png" lexicographically, and within each mime-type
        // group, names fall back to natural order.
        assertEquals(listOf("a-doc.pdf", "z-doc.pdf", "a-image.png", "b-image.png"), sorted)
    }

    @Test
    fun `type sort treats a null mime type as sorting before any known type`() {
        val spec = SortSpec(field = SortField.TYPE, ascending = true, foldersFirst = false)
        val nodes = listOf(
            node("known.txt", mimeType = "text/plain"),
            node("unknown", mimeType = null),
        )

        val sorted = nodes.sortedWith(spec.comparator()).map { it.name }

        assertEquals(listOf("unknown", "known.txt"), sorted)
    }

    @Test
    fun `ties on a non-name field preserve input order (stable sort, no implicit name tiebreak)`() {
        val spec = SortSpec(field = SortField.SIZE, ascending = true, foldersFirst = false)
        val nodes = listOf(node("b", size = 5), node("a", size = 5))

        // SIZE ties aren't broken by name in the current comparator design — this test
        // documents that (stable sort keeps input order) rather than asserting a
        // name-based tiebreak that isn't part of the spec.
        val sorted = nodes.sortedWith(spec.comparator()).map { it.name }

        assertEquals(listOf("b", "a"), sorted)
    }
}
