package com.devbehindyou.refract.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FileCollectionTest {
    private fun node(
        name: String,
        mime: String? = null,
        directory: Boolean = false,
    ) = FileNode(
        FileNodeId.file("/test/$name"), name, name, mime, 0, 0, directory, false,
        null, StorageType.INTERNAL_SHARED, AccessFlags.READ_ONLY, null, null,
    )

    @ParameterizedTest
    @CsvSource(
        "picture.HEIC,IMAGES", "photo.avif,IMAGES", "clip.mkv,VIDEOS", "song.opus,AUDIO",
        "notes.docx,DOCUMENTS", "sheet.xlsx,DOCUMENTS", "book.pdf,PDFS", "notes.TXT,TEXT",
        "backup.7z,ARCHIVES", "backup.tar.gz,ARCHIVES", "installer.APK,APKS", "book.epub,EBOOKS",
        "font.woff2,FONTS", "unknown.blob,OTHER",
    )
    fun recognizesFilesOutsideDefaultMediaFolders(
        name: String,
        expected: FileCollection,
    ) {
        val file = node(name)
        assertTrue(expected.matches(file))
        listOf(
            FileCollection.IMAGES,
            FileCollection.VIDEOS,
            FileCollection.AUDIO,
            FileCollection.ARCHIVES,
            FileCollection.APKS,
        ).filter { it != expected }.forEach {
            assertFalse(it.matches(file), "$name must not appear in ${it.title}")
        }
    }

    @Test
    fun usesMimeForExtensionlessMediaAndIncludesPdfInDocuments() {
        assertTrue(FileCollection.IMAGES.matches(node("camera-export", "image/jpeg")))
        assertTrue(FileCollection.DOCUMENTS.matches(node("book.pdf")))
        assertFalse(FileCollection.ARCHIVES.matches(node("installer.apk", "application/zip")))
    }

    @Test
    fun directoriesNeverAppearAsCategoryFiles() {
        FileCollection.entries.forEach { assertFalse(it.matches(node("photos.jpg", directory = true))) }
    }
}
