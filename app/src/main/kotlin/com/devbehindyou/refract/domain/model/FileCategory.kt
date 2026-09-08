package com.devbehindyou.refract.domain.model

/**
 * The Home screen's category tiles (`architecture/MVVM.md`). Classification logic (mime type
 * -> category) belongs to a use case in a later phase — this phase only defines the vocabulary.
 */
enum class FileCategory { IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, DOWNLOAD, OTHER }
