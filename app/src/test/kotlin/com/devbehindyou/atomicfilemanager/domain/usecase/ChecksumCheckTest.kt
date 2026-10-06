package com.devbehindyou.atomicfilemanager.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChecksumCheckTest {
    private val hashes =
        FileChecksums(
            md5 = "9e107d9d372bb6826bd81d3542a419d6",
            sha256 = "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
        )

    @Test
    fun `pasted checksums match regardless of case, spaces and a prefix`() {
        assertEquals(ChecksumVerdict.Match("MD5"), checkExpectedChecksum("  9E107D9D372BB6826BD81D3542A419D6 ", hashes))
        assertEquals(
            ChecksumVerdict.Match("SHA-256"),
            checkExpectedChecksum(
                "sha256: d7a8fbb3 07d78094 69ca9abc b0082e4f 8d5651e4 6d3cdb76 2d02d0bf 37c9e592",
                hashes,
            ),
        )
    }

    @Test
    fun `a different hash of the right length is a mismatch`() {
        assertEquals(ChecksumVerdict.Mismatch("MD5"), checkExpectedChecksum("0".repeat(32), hashes))
        assertEquals(ChecksumVerdict.Mismatch("SHA-256"), checkExpectedChecksum("a".repeat(64), hashes))
    }

    @Test
    fun `other input is explained, not judged`() {
        assertEquals(ChecksumVerdict.Empty, checkExpectedChecksum("   ", hashes))
        assertEquals(ChecksumVerdict.Unsupported("SHA-1"), checkExpectedChecksum("a".repeat(40), hashes))
        assertEquals(ChecksumVerdict.NotAHash, checkExpectedChecksum("not a hash", hashes))
        assertEquals(ChecksumVerdict.NotAHash, checkExpectedChecksum("abc", hashes))
    }

    @Test
    fun `same content needs equal size and equal SHA-256`() {
        assertTrue(sameContent(10, hashes, 10, hashes.copy(sha256 = hashes.sha256.uppercase())))
        assertFalse(sameContent(10, hashes, 11, hashes))
        assertFalse(sameContent(10, hashes, 10, hashes.copy(sha256 = "0".repeat(64))))
    }
}
