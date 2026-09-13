package com.devbehindyou.refract.data.backend

import android.net.Uri
import android.provider.DocumentsContract
import com.devbehindyou.refract.domain.model.FileNodeId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests [toSafRef]/[safId] directly — the URI encode/decode logic is the part of
 * [SafBackend] I was least certain about getting right (see its class KDoc). This doesn't
 * need a real or fake `DocumentsProvider`: `Uri`/`DocumentsContract`'s `build*`/`get*`
 * methods are pure string manipulation, not `ContentResolver` I/O, so Robolectric's `Uri`
 * shadow (one of its most solid) is enough — no provider round-trip involved.
 */
@RunWith(RobolectricTestRunner::class)
class SafUriMappingTest {
    private val treeUri: Uri =
        DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download",
        )

    @Test
    fun `a tree-root document id round-trips through toSafRef`() {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val id = safId(treeUri, rootDocId)

        val ref = id.toSafRef()

        assertEquals(treeUri, ref.treeUri)
        assertEquals(rootDocId, ref.documentId)
    }

    @Test
    fun `a descendant document id round-trips through toSafRef`() {
        val childDocId = "primary:Download/subfolder/photo.jpg"
        val id = safId(treeUri, childDocId)

        val ref = id.toSafRef()

        assertEquals(treeUri, ref.treeUri)
        assertEquals(childDocId, ref.documentId)
    }

    @Test
    fun `safId then toSafRef recovers the same authority regardless of nesting depth`() {
        val ids = listOf("primary:Download", "primary:Download/a", "primary:Download/a/b/c/d/e")

        ids.forEach { docId ->
            val ref = safId(treeUri, docId).toSafRef()
            assertEquals(treeUri.authority, ref.documentUri.authority)
            assertEquals(docId, ref.documentId)
        }
    }

    @Test
    fun `FileNodeId parse accepts what safId produces`() {
        val id = safId(treeUri, "primary:Download/nested file.txt")

        val parsed = FileNodeId.parse(id.raw)

        assertEquals(id, parsed)
    }
}
