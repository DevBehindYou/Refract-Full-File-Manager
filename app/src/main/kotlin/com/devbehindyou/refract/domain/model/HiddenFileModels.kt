package com.devbehindyou.refract.domain.model

enum class HideMode {
    GALLERY,
    FAST_OBSCURE,
    PRIVATE_STORAGE,
}

enum class JournalState {
    PREPARING,
    HEADER_TRANSFORMED,
    RENAMED,
    COMMITTED,
    ROLLBACK_REQUIRED,
    RESTORED,
}

data class HiddenItem(
    val id: String,
    val originalLocation: String,
    val currentLocation: String,
    val originalName: String,
    val size: Long,
    val mode: HideMode,
    val hiddenAt: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = true,
)

data class HideJournalEntry(
    val operationId: String,
    val originalPath: String,
    val currentPath: String,
    val originalName: String,
    val state: JournalState,
    val mode: HideMode,
    val timestamp: Long = System.currentTimeMillis(),
)
