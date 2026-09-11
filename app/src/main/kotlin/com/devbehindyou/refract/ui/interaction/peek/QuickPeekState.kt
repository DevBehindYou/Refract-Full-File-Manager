package com.devbehindyou.refract.ui.interaction.peek

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.devbehindyou.refract.domain.model.FileNode

class QuickPeekController {
    var activeNode by mutableStateOf<FileNode?>(null)
        private set

    var previewBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var resolution by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    val isPeeking: Boolean get() = activeNode != null

    fun startPeek(node: FileNode) {
        activeNode = node
        previewBitmap = null
        resolution = null
        isLoading = true
    }

    fun setPreview(bitmap: Bitmap?, res: String? = null) {
        previewBitmap = bitmap
        resolution = res
        isLoading = false
    }

    fun dismiss() {
        activeNode = null
        previewBitmap = null
        resolution = null
        isLoading = false
    }
}
