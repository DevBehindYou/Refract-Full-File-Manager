package com.devbehindyou.refract.domain.testing

import com.devbehindyou.refract.domain.model.FileError
import com.devbehindyou.refract.domain.model.FileResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * These four decorators (`FaultInjectingBackends.kt`) exist to make failure paths
 * deterministically testable for Phase 3/7 — if they themselves have bugs, that
 * guarantee is worthless. Tested here, against `InMemoryBackend` as the wrapped
 * delegate, the same way real callers will use them.
 */
class FailAfterNBytesTest {
    @Test
    fun `writing fewer bytes than the threshold succeeds completely`() =
        runTest {
            val real = InMemoryBackend()
            val backend = FailAfterNBytes(thresholdBytes = 100, delegate = real)
            val target = (backend.openOutput(real.rootId, "small.txt", "text/plain") as FileResult.Success).value

            target.stream().use { it.write("short".encodeToByteArray()) }
            val node = (target.toNode() as FileResult.Success).value

            assertEquals(5L, node.size)
        }

    @Test
    fun `writing past the threshold throws IOException`() =
        runTest {
            val real = InMemoryBackend()
            val backend = FailAfterNBytes(thresholdBytes = 4, delegate = real)
            val target = (backend.openOutput(real.rootId, "big.txt", "text/plain") as FileResult.Success).value

            val exception =
                assertThrows(IOException::class.java) {
                    target.stream().use { it.write("this is definitely more than four bytes".encodeToByteArray()) }
                }

            assertTrue(exception.message?.contains("4 bytes") == true)
        }

    @Test
    fun `bytes written before the threshold are preserved in the underlying backend`() =
        runTest {
            val real = InMemoryBackend()
            val backend = FailAfterNBytes(thresholdBytes = 4, delegate = real)
            val target = (backend.openOutput(real.rootId, "partial.txt", "text/plain") as FileResult.Success).value

            runCatching { target.stream().use { it.write("12345678".encodeToByteArray()) } }

            val children = (real.listChildren(real.rootId).first() as FileResult.Success).value
            val written = children.firstOrNull { it.name == "partial.txt" }
            assertEquals(4L, written?.size)
        }

    @Test
    fun `read operations pass through unaffected`() =
        runTest {
            val real = InMemoryBackend()
            val seeded = real.putFile(real.rootId, "already-there.txt", "hello".encodeToByteArray())
            val backend = FailAfterNBytes(thresholdBytes = 1, delegate = real)

            val result = backend.getNode(seeded)

            assertTrue(result is FileResult.Success)
        }
}

class DenyWriteTest {
    @Test
    fun `openOutput fails with AccessDenied`() =
        runTest {
            val real = InMemoryBackend()
            val backend = DenyWrite(real)

            val result = backend.openOutput(real.rootId, "new.txt", "text/plain")

            assertTrue(result is FileResult.Failure)
            assertTrue((result as FileResult.Failure).error is FileError.AccessDenied)
        }

    @Test
    fun `createDirectory fails with AccessDenied`() =
        runTest {
            val real = InMemoryBackend()
            val backend = DenyWrite(real)

            val result = backend.createDirectory(real.rootId, "new-dir")

            assertTrue(result is FileResult.Failure)
            assertTrue((result as FileResult.Failure).error is FileError.AccessDenied)
        }

    @Test
    fun `delete fails with AccessDenied and the node is not actually removed`() =
        runTest {
            val real = InMemoryBackend()
            val existing = (real.createDirectory(real.rootId, "keep-me") as FileResult.Success).value
            val backend = DenyWrite(real)

            val result = backend.delete(existing.id)
            val stillThere = real.getNode(existing.id)

            assertTrue(result is FileResult.Failure)
            assertTrue(stillThere is FileResult.Success)
        }

    @Test
    fun `rename fails with AccessDenied`() =
        runTest {
            val real = InMemoryBackend()
            val existing = (real.createDirectory(real.rootId, "original") as FileResult.Success).value
            val backend = DenyWrite(real)

            val result = backend.rename(existing.id, "renamed")

            assertTrue(result is FileResult.Failure)
            assertTrue((result as FileResult.Failure).error is FileError.AccessDenied)
        }

    @Test
    fun `moveWithin fails with AccessDenied`() =
        runTest {
            val real = InMemoryBackend()
            val destination = (real.createDirectory(real.rootId, "dest") as FileResult.Success).value
            val moving = (real.createDirectory(real.rootId, "moving") as FileResult.Success).value
            val backend = DenyWrite(real)

            val result = backend.moveWithin(moving.id, destination.id)

            assertTrue(result is FileResult.Failure)
            assertTrue((result as FileResult.Failure).error is FileError.AccessDenied)
        }

    @Test
    fun `reads pass through unaffected`() =
        runTest {
            val real = InMemoryBackend()
            val existing = (real.createDirectory(real.rootId, "readable") as FileResult.Success).value
            val backend = DenyWrite(real)

            val node = backend.getNode(existing.id)
            val children = backend.listChildren(real.rootId).first()
            val exists = backend.exists(real.rootId, "readable")

            assertTrue(node is FileResult.Success)
            assertTrue(children is FileResult.Success)
            assertTrue(exists)
        }
}

class SlowBackendTest {
    @Test
    fun `getNode waits at least delayMillis of virtual time before returning`() =
        runTest {
            val real = InMemoryBackend()
            val backend = SlowBackend(delayMillis = 500, delegate = real)

            val before = testScheduler.currentTime
            backend.getNode(real.rootId)
            val elapsed = testScheduler.currentTime - before

            assertTrue(elapsed >= 500)
        }

    @Test
    fun `the delegate's result is still returned correctly after the delay`() =
        runTest {
            val real = InMemoryBackend()
            val backend = SlowBackend(delayMillis = 100, delegate = real)

            val result = backend.getNode(real.rootId)

            assertTrue(result is FileResult.Success)
            assertEquals(real.rootId, (result as FileResult.Success).value.id)
        }

    @Test
    fun `listChildren itself is not delayed, since it is not a suspend function`() =
        runTest {
            // StorageBackend#listChildren returns a Flow directly rather than being `suspend`,
            // so SlowBackend structurally cannot delay before returning it (only collecting the
            // flow could be delayed, and this decorator doesn't wrap the flow to do that).
            // Documented here rather than left as an implicit, easy-to-assume-otherwise gap.
            val real = InMemoryBackend()
            val backend = SlowBackend(delayMillis = 10_000, delegate = real)

            val before = testScheduler.currentTime
            backend.listChildren(real.rootId)
            val elapsed = testScheduler.currentTime - before

            assertEquals(0L, elapsed)
        }
}

class VanishingSourceTest {
    @Test
    fun `the target can be read exactly survivesReads times before vanishing`() =
        runTest {
            val real = InMemoryBackend()
            val target = (real.createDirectory(real.rootId, "here-then-gone") as FileResult.Success).value
            val backend = VanishingSource(target = target.id, survivesReads = 2, delegate = real)

            val first = backend.getNode(target.id)
            val second = backend.getNode(target.id)
            val third = backend.getNode(target.id)

            assertTrue(first is FileResult.Success)
            assertTrue(second is FileResult.Success)
            assertTrue(third is FileResult.Failure)
            assertTrue((third as FileResult.Failure).error is FileError.FileNotFound)
        }

    @Test
    fun `openInput on the target also vanishes after survivesReads`() =
        runTest {
            val real = InMemoryBackend()
            val seeded = real.putFile(real.rootId, "readable-once.txt", "data".encodeToByteArray())
            val backend = VanishingSource(target = seeded, survivesReads = 1, delegate = real)

            val first = backend.openInput(seeded)
            val second = backend.openInput(seeded)

            assertTrue(first is FileResult.Success)
            assertTrue(second is FileResult.Failure)
        }

    @Test
    fun `nodes other than the target are never affected`() =
        runTest {
            val real = InMemoryBackend()
            val target = (real.createDirectory(real.rootId, "vanishing") as FileResult.Success).value
            val untouched = (real.createDirectory(real.rootId, "unaffected") as FileResult.Success).value
            // survivesReads = 0 means even the target's first read vanishes immediately.
            val backend = VanishingSource(target = target.id, survivesReads = 0, delegate = real)

            backend.getNode(target.id)
            val untouchedResult = backend.getNode(untouched.id)

            assertTrue(untouchedResult is FileResult.Success)
        }
}
