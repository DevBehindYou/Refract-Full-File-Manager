package com.devbehindyou.atomicfilemanager.data.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareHtmlTest {
    @Test
    fun `names are escaped`() {
        val html =
            ShareHtml.listing(
                "Download",
                "",
                listOf(ShareHtml.Entry("<script>x</script>.txt", "<script>x</script>.txt", false, 10)),
                allowUpload = false,
            )
        assertFalse("<script>x" in html)
        assertTrue("&lt;script&gt;x&lt;/script&gt;.txt" in html)
    }

    @Test
    fun `upload form only when allowed`() {
        assertFalse("type=\"file\"" in ShareHtml.listing("D", "", emptyList(), allowUpload = false))
        assertTrue("type=\"file\"" in ShareHtml.listing("D", "", emptyList(), allowUpload = true))
    }

    @Test
    fun `safe upload names`() {
        assertTrue(ShareHtml.isSafeName("photo 1.jpg"))
        listOf("", " ", ".env", "a/b", "a\\b", "x\u0000y", "a".repeat(256)).forEach {
            assertFalse(ShareHtml.isSafeName(it), it)
        }
    }

    @Test
    fun `join and escape`() {
        assertEquals("a", ShareHtml.join("", "a"))
        assertEquals("x/a", ShareHtml.join("x", "a"))
        assertEquals("&amp;&quot;&#39;", ShareHtml.escape("&\"'"))
    }
}
