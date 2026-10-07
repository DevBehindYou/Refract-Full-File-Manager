package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.data.preview.ExifTags
import com.devbehindyou.atomicfilemanager.data.preview.ImageMetadata
import com.devbehindyou.atomicfilemanager.data.preview.MetadataStrip
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataTextTest {
    private val photo =
        ImageMetadata(
            make = "Google",
            model = "Pixel 8",
            takenAt = "2024:05:01 13:45:10",
            iso = 100,
            exposureSeconds = 0.008,
            fNumber = 1.7,
            focalLengthMm = 6.9,
            width = 4000,
            height = 3000,
            latitude = 51.5007,
            longitude = -0.12463,
        )

    @Test
    fun `facts list camera, exposure and location`() {
        val facts = MetadataText.facts(photo).associate { it.label to it.value }
        assertEquals("Google Pixel 8", facts["Camera"])
        assertEquals("2024-05-01 13:45", facts["Taken"])
        assertEquals("1/125 s · f/1.7 · ISO 100 · 6.9 mm", facts["Exposure"])
        assertEquals("4000 × 3000", facts["Pixels"])
        assertEquals("51.50070, -0.12463", facts["Location"])
    }

    @Test
    fun `absent fields are left out`() {
        assertTrue(MetadataText.facts(ImageMetadata()).isEmpty())
        assertTrue(ImageMetadata().isEmpty)
        assertFalse(ImageMetadata(latitude = 1.0).hasLocation)
    }

    @Test
    fun `camera does not repeat the make`() {
        assertEquals("Canon EOS R5", MetadataText.camera(ImageMetadata(make = "Canon", model = "Canon EOS R5")))
        assertEquals("Sony", MetadataText.camera(ImageMetadata(make = "Sony")))
        assertNull(MetadataText.camera(ImageMetadata()))
    }

    @Test
    fun `shutter and dates format sensibly`() {
        assertEquals("2 s", MetadataText.shutter(2.0))
        assertEquals("1/60 s", MetadataText.shutter(1.0 / 60))
        assertEquals("yesterday", MetadataText.takenAt("yesterday"))
    }

    @Test
    fun `location strip removes only GPS tags`() {
        val removed = ExifTags.removedBy(MetadataStrip.LOCATION)
        assertTrue(removed.all { it.startsWith("GPS") })
        assertTrue("GPSLatitude" in removed && "GPSLongitude" in removed)
        assertFalse("Orientation" in ExifTags.removedBy(MetadataStrip.ALL))
        assertTrue("Make" in ExifTags.removedBy(MetadataStrip.ALL))
    }

    @Test
    fun `clean check matches the promise`() {
        val noGps = photo.copy(latitude = null, longitude = null)
        assertTrue(ExifTags.isClean(noGps, MetadataStrip.LOCATION))
        assertFalse(ExifTags.isClean(noGps, MetadataStrip.ALL))
        assertFalse(ExifTags.isClean(photo, MetadataStrip.LOCATION))
        assertTrue(ExifTags.isClean(ImageMetadata(width = 10, height = 10), MetadataStrip.ALL))
    }

    @Test
    fun `only rewritable formats can be stripped`() {
        assertTrue(ExifTags.canStrip("image/jpeg"))
        assertTrue(ExifTags.canStrip("IMAGE/PNG"))
        assertFalse(ExifTags.canStrip("image/heic"))
        assertFalse(ExifTags.canStrip(null))
        assertEquals("IMG_1 (clean).jpg", ExifTags.cleanName("IMG_1.jpg"))
        assertEquals("photo (clean)", ExifTags.cleanName("photo"))
    }
}
