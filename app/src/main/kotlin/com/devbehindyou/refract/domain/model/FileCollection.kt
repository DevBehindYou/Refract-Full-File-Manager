package com.devbehindyou.refract.domain.model

enum class FileCollection(val title: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    APKS("APKs"),
    PDFS("PDFs"),
    TEXT("Text and code"),
    EBOOKS("E-books"),
    FONTS("Fonts"),
    OTHER("Other files"),
    ;

    fun matches(node: FileNode): Boolean {
        if (node.isDirectory) return false
        val ext = node.name.substringAfterLast('.', "").lowercase()
        val mime = node.mimeType.orEmpty().lowercase()
        val base =
            extensions[ext] ?: when {
                mime == "application/vnd.android.package-archive" -> APKS
                mime.startsWith("image/") -> IMAGES
                mime.startsWith("video/") -> VIDEOS
                mime.startsWith("audio/") -> AUDIO
                mime.startsWith("font/") -> FONTS
                mime == "application/epub+zip" -> EBOOKS
                mime == "application/pdf" -> PDFS
                mime.startsWith("text/") -> TEXT
                mime in archiveMimeTypes -> ARCHIVES
                mime.contains("officedocument") || mime.contains("opendocument") || mime in officeMimeTypes -> DOCUMENTS
                else -> OTHER
            }
        return if (this == DOCUMENTS) base in documentGroup else this == base
    }

    companion object {
        private val extensions =
            buildMap {
                fun add(
                    collection: FileCollection,
                    words: String,
                ) {
                    words.split(' ').forEach { put(it, collection) }
                }
                add(IMAGES, "jpg jpeg png gif webp heic heif avif bmp svg tif tiff dng raw ico")
                add(VIDEOS, "mp4 mkv webm avi mov 3gp m4v ts mpeg mpg wmv flv")
                add(AUDIO, "mp3 m4a aac flac wav ogg opus wma amr aiff mid midi")
                add(DOCUMENTS, "doc docx xls xlsx ppt pptx odt ods odp rtf pages numbers key")
                add(ARCHIVES, "zip rar 7z tar gz bz2 xz zst tgz iso")
                add(APKS, "apk")
                add(PDFS, "pdf")
                add(TEXT, "txt md csv json xml log html css js kt java py sh yaml yml ini")
                add(EBOOKS, "epub mobi azw azw3 fb2")
                add(FONTS, "ttf otf woff woff2")
            }
        private val documentGroup = setOf(DOCUMENTS, PDFS, TEXT, EBOOKS)
        private val archiveMimeTypes =
            setOf(
                "application/zip",
                "application/x-rar-compressed",
                "application/x-7z-compressed",
                "application/x-tar",
                "application/gzip",
            )
        private val officeMimeTypes =
            setOf(
                "application/msword",
                "application/vnd.ms-excel",
                "application/vnd.ms-powerpoint",
                "application/rtf",
            )
    }
}
