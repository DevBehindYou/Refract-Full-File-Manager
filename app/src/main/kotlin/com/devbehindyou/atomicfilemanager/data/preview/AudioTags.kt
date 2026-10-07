package com.devbehindyou.atomicfilemanager.data.preview

/**
 * Tags beyond title, artist and album (ALL_IN_ONE_PLAN.md 4.2), as the file stores them. Every
 * field is null when absent; numbers are kept as written ("3/12") because tag formats differ.
 */
data class AudioTags(
    val albumArtist: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val track: String? = null,
    val disc: String? = null,
    val composer: String? = null,
    val bitrateBps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
)
