package com.devbehindyou.atomicfilemanager.domain.usecase

/** A saved folder sync (ALL_IN_ONE_PLAN.md 4.1): re-run with one tap, never in the background. */
data class SyncPair(
    val leftRaw: String,
    val rightRaw: String,
    val mode: SyncMode,
)

/** Saved pairs as one string; pure so it is unit-tested. */
object SyncPairs {
    const val MAX = 10
    private const val ROW = "\u001E"
    private const val FIELD = "\u001F"

    /** Adds or updates [pair] at the top; the same two folders are kept once. */
    fun add(
        pairs: List<SyncPair>,
        pair: SyncPair,
    ): List<SyncPair> = (listOf(pair) + pairs.filterNot { it.sameFolders(pair) }).take(MAX)

    fun remove(
        pairs: List<SyncPair>,
        pair: SyncPair,
    ): List<SyncPair> = pairs.filterNot { it.sameFolders(pair) }

    fun find(
        pairs: List<SyncPair>,
        leftRaw: String,
        rightRaw: String,
    ): SyncPair? = pairs.firstOrNull { it.leftRaw == leftRaw && it.rightRaw == rightRaw }

    fun encode(pairs: List<SyncPair>): String =
        pairs.joinToString(ROW) { listOf(it.leftRaw, it.rightRaw, it.mode.name).joinToString(FIELD) }

    /** Rows that don't parse, such as ones from a newer version, are skipped. */
    fun decode(saved: String?): List<SyncPair> =
        saved.orEmpty().split(ROW).mapNotNull { row ->
            val f = row.split(FIELD)
            val mode = SyncMode.entries.firstOrNull { it.name == f.getOrNull(2) } ?: return@mapNotNull null
            val left = f[0].takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val right = f[1].takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SyncPair(left, right, mode)
        }.take(MAX)

    private fun SyncPair.sameFolders(other: SyncPair) = leftRaw == other.leftRaw && rightRaw == other.rightRaw
}
