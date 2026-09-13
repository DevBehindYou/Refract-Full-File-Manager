package com.devbehindyou.refract.domain.model

import java.text.Collator
import java.util.Locale

enum class SortField { NAME, SIZE, DATE, TYPE }

data class SortSpec(
    val field: SortField,
    val ascending: Boolean,
    val foldersFirst: Boolean,
) {
    companion object {
        val DEFAULT = SortSpec(field = SortField.NAME, ascending = true, foldersFirst = true)
    }
}

/**
 * Compares strings the way people expect: alphabetic runs compare via locale-aware
 * [Collator] (case-insensitive, accent-sensitive — `Collator.SECONDARY`), numeric runs
 * compare by numeric value, so `"file2"` sorts before `"file10"` (roadmap Phase 2 AC4)
 * and leading zeros don't affect ordering (`"file007"` == `"file7"` numerically).
 * Digit runs of any length are compared safely without parsing to a fixed-width numeric
 * type, by comparing (post-leading-zero-strip) length first, then lexicographically.
 *
 * Not a singleton `object`: a [Collator] is somewhat expensive to construct, and
 * caching one forever in a singleton would freeze whatever locale was active the first
 * time it was touched, ignoring a later runtime locale change. [comparator] below
 * constructs one fresh per sort operation instead — once per list sort, not once per
 * pairwise comparison — which avoids both problems. [ROOT] is a fixed, locale-independent
 * instance for tests and anywhere determinism matters more than the user's live locale.
 */
class NaturalOrderComparator(locale: Locale = Locale.getDefault()) : Comparator<String> {
    private val collator: Collator =
        Collator.getInstance(locale).apply {
            strength = Collator.SECONDARY
        }

    override fun compare(
        a: String,
        b: String,
    ): Int {
        val segmentsA = tokenize(a)
        val segmentsB = tokenize(b)
        var i = 0
        while (i < segmentsA.size && i < segmentsB.size) {
            val segA = segmentsA[i]
            val segB = segmentsB[i]
            val cmp =
                if (segA[0].isDigit() && segB[0].isDigit()) {
                    compareNumeric(segA, segB)
                } else {
                    collator.compare(segA, segB)
                }
            if (cmp != 0) return cmp
            i++
        }
        return segmentsA.size.compareTo(segmentsB.size)
    }

    companion object {
        val ROOT: NaturalOrderComparator = NaturalOrderComparator(Locale.ROOT)

        /** Splits into alternating digit-run / non-digit-run segments, e.g.
         * `"file10.txt"` -> `["file", "10", ".txt"]`. */
        private fun tokenize(s: String): List<String> {
            if (s.isEmpty()) return emptyList()
            val segments = mutableListOf<String>()
            var start = 0
            var currentIsDigit = s[0].isDigit()
            for (i in 1..s.length) {
                if (i == s.length || s[i].isDigit() != currentIsDigit) {
                    segments.add(s.substring(start, i))
                    start = i
                    if (i < s.length) currentIsDigit = s[i].isDigit()
                }
            }
            return segments
        }

        private fun compareNumeric(
            a: String,
            b: String,
        ): Int {
            val trimmedA = a.trimStart('0').ifEmpty { "0" }
            val trimmedB = b.trimStart('0').ifEmpty { "0" }
            val byLength = trimmedA.length.compareTo(trimmedB.length)
            return if (byLength != 0) byLength else trimmedA.compareTo(trimmedB)
        }
    }
}

/** Folders-first is applied independently of [SortSpec.ascending] — reversing the sort field
 * reverses within each group, not the grouping itself, matching common file-manager UX. */
fun SortSpec.comparator(): Comparator<FileNode> {
    val natural = NaturalOrderComparator()
    val byField: Comparator<FileNode> =
        when (field) {
            SortField.NAME -> Comparator { a, b -> natural.compare(a.name, b.name) }
            SortField.SIZE -> Comparator.comparingLong(FileNode::size)
            SortField.DATE -> Comparator.comparingLong(FileNode::modifiedAt)
            SortField.TYPE ->
                compareBy<FileNode> { it.mimeType ?: "" }
                    .thenComparator { a, b -> natural.compare(a.name, b.name) }
        }
    val directed = if (ascending) byField else byField.reversed()
    return if (foldersFirst) {
        Comparator<FileNode> { a, b -> b.isDirectory.compareTo(a.isDirectory) }.then(directed)
    } else {
        directed
    }
}
