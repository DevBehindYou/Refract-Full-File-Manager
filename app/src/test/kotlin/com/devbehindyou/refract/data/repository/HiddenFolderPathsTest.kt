package com.devbehindyou.refract.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HiddenFolderPathsTest {
    @Test
    fun `volume root of internal storage and of an SD card`() {
        assertEquals(File("/storage/emulated/0"), volumeRootOf(File("/storage/emulated/0/Download/a.txt")))
        assertEquals(File("/storage/F929-FA97"), volumeRootOf(File("/storage/F929-FA97/DCIM/a.jpg")))
    }

    @Test
    fun `paths outside a known storage volume have no root`() {
        assertNull(volumeRootOf(File("/data/data/app/files/a.txt")))
        assertNull(volumeRootOf(File("/storage/emulated")))
        assertNull(volumeRootOf(File("/storage/self/primary/a.txt")))
    }

    @Test
    fun `a taken name gets a numbered suffix before the extension`(
        @TempDir dir: File,
    ) {
        assertEquals(File(dir, "photo.jpg"), uniqueChild(dir, "photo.jpg"))
        File(dir, "photo.jpg").writeText("x")
        assertEquals(File(dir, "photo (1).jpg"), uniqueChild(dir, "photo.jpg"))
        File(dir, "photo (1).jpg").writeText("x")
        assertEquals(File(dir, "photo (2).jpg"), uniqueChild(dir, "photo.jpg"))
        File(dir, "notes").writeText("x")
        assertEquals(File(dir, "notes (1)"), uniqueChild(dir, "notes"))
    }
}
