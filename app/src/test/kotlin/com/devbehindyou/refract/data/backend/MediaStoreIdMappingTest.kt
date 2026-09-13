package com.devbehindyou.refract.data.backend

import android.provider.MediaStore
import com.devbehindyou.refract.domain.model.AccessTarget
import com.devbehindyou.refract.domain.model.FileNodeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaStoreIdMappingTest {
    @Test
    fun `a real row id parses to a non-root ref with the right collection uri`() {
        val id = FileNodeId.media("external", "images", 10432L)

        val ref = id.toMediaRef()

        assertFalse(ref.isCollectionRoot)
        assertEquals(10432L, ref.rowId)
        assertEquals(MediaStore.Images.Media.getContentUri("external"), ref.collectionUri)
        assertEquals(AccessTarget.MediaImages, ref.accessTarget)
    }

    @Test
    fun `MediaStoreBackend rootId produces a ref that reports as a collection root`() {
        val id = MediaStoreBackend.rootId("external", "video")

        val ref = id.toMediaRef()

        assertTrue(ref.isCollectionRoot)
        assertEquals(AccessTarget.MediaVideo, ref.accessTarget)
    }

    @Test
    fun `each of the three known collections maps to the right AccessTarget`() {
        assertEquals(AccessTarget.MediaImages, FileNodeId.media("external", "images", 1L).toMediaRef().accessTarget)
        assertEquals(AccessTarget.MediaVideo, FileNodeId.media("external", "video", 1L).toMediaRef().accessTarget)
        assertEquals(AccessTarget.MediaAudio, FileNodeId.media("external", "audio", 1L).toMediaRef().accessTarget)
    }

    @Test
    fun `FileNodeId parse accepts what MediaStoreBackend rootId produces`() {
        val id = MediaStoreBackend.rootId("external", "audio")

        val parsed = FileNodeId.parse(id.raw)

        assertEquals(id, parsed)
    }
}
