package com.devbehindyou.atomicfilemanager.ui.components.preview

import com.devbehindyou.atomicfilemanager.data.preview.AudioTags
import com.devbehindyou.atomicfilemanager.data.preview.MediaMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioTagTextTest {
    private fun meta(
        tags: AudioTags,
        artist: String? = "Nina",
    ) = MediaMetadata(
        title = "Song",
        artist = artist,
        album = "Album",
        durationMs = 1000,
        width = 0,
        height = 0,
        artwork = null,
        mimeType = "audio/mpeg",
        tags = tags,
    )

    @Test
    fun `details list what the file has`() {
        val facts =
            AudioTagText.facts(
                meta(
                    AudioTags(
                        albumArtist = "Various",
                        year = "1999",
                        genre = "Jazz",
                        track = "3/12",
                        bitrateBps = 320_000,
                        sampleRateHz = 44_100,
                        bitsPerSample = 16,
                    ),
                ),
            ).associate { it.label to it.value }
        assertEquals("Various", facts["Album artist"])
        assertEquals("1999", facts["Year"])
        assertEquals("3/12", facts["Track"])
        assertEquals("320 kbps · 44.1 kHz · 16-bit", facts["Quality"])
        assertEquals("audio/mpeg", facts["Format"])
    }

    @Test
    fun `album artist equal to the artist is not repeated`() {
        val labels = AudioTagText.facts(meta(AudioTags(albumArtist = "Nina", year = "2001"))).map { it.label }
        assertTrue("Album artist" !in labels)
    }

    @Test
    fun `no tags means no details, not just a format line`() {
        assertTrue(AudioTagText.facts(meta(AudioTags())).isEmpty())
    }

    @Test
    fun `quality leaves out what is unknown`() {
        assertEquals("48 kHz", AudioTagText.quality(null, 48_000, null))
        assertEquals("128 kbps", AudioTagText.quality(127_600, null, null))
        assertNull(AudioTagText.quality(null, null, null))
    }
}
