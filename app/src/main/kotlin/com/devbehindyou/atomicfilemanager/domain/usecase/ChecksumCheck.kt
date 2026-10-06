package com.devbehindyou.atomicfilemanager.domain.usecase

/** The result of checking a pasted checksum against a file (ALL_IN_ONE_PLAN.md 2.8). */
sealed interface ChecksumVerdict {
    data class Match(val algorithm: String) : ChecksumVerdict

    data class Mismatch(val algorithm: String) : ChecksumVerdict

    /** Looks like a hash of a kind the app doesn't calculate (for example SHA-1). */
    data class Unsupported(val algorithm: String) : ChecksumVerdict

    /** Not a hex hash at all. */
    data object NotAHash : ChecksumVerdict

    /** Nothing typed yet. */
    data object Empty : ChecksumVerdict
}

/**
 * Compares [expected] (as copied from a download page) with [hashes]. Case, spaces and an
 * "sha256:"-style prefix are ignored; the algorithm is told apart by length.
 */
fun checkExpectedChecksum(
    expected: String,
    hashes: FileChecksums,
): ChecksumVerdict {
    val cleaned =
        expected
            .trim()
            .substringAfter(':')
            .filterNot { it.isWhitespace() }
            .lowercase()
    if (cleaned.isEmpty()) return ChecksumVerdict.Empty
    if (cleaned.any { it !in HEX }) return ChecksumVerdict.NotAHash
    return when (cleaned.length) {
        MD5_LENGTH -> verdict("MD5", cleaned == hashes.md5.lowercase())
        SHA256_LENGTH -> verdict("SHA-256", cleaned == hashes.sha256.lowercase())
        SHA1_LENGTH -> ChecksumVerdict.Unsupported("SHA-1")
        SHA512_LENGTH -> ChecksumVerdict.Unsupported("SHA-512")
        else -> ChecksumVerdict.NotAHash
    }
}

/** True when two files have the same content, judged by size and then SHA-256. */
fun sameContent(
    sizeA: Long,
    hashesA: FileChecksums,
    sizeB: Long,
    hashesB: FileChecksums,
): Boolean = sizeA == sizeB && hashesA.sha256.equals(hashesB.sha256, ignoreCase = true)

private fun verdict(
    algorithm: String,
    match: Boolean,
): ChecksumVerdict = if (match) ChecksumVerdict.Match(algorithm) else ChecksumVerdict.Mismatch(algorithm)

private const val HEX = "0123456789abcdef"
private const val MD5_LENGTH = 32
private const val SHA1_LENGTH = 40
private const val SHA256_LENGTH = 64
private const val SHA512_LENGTH = 128
