package com.devbehindyou.atomicfilemanager.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicErrorState
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFact
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFactSheet
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.ArchiveEntryInfo
import com.devbehindyou.atomicfilemanager.domain.usecase.FileChecksums
import com.devbehindyou.atomicfilemanager.domain.usecase.TextContent
import com.devbehindyou.atomicfilemanager.ui.components.preview.AudioPreviewContent
import com.devbehindyou.atomicfilemanager.ui.components.preview.MarkdownPreviewContent
import com.devbehindyou.atomicfilemanager.ui.components.preview.VideoPreviewContent
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.launch

private enum class PreviewType {
    IMAGE,
    PDF,
    TEXT,
    MARKDOWN,
    ARCHIVE,
    AUDIO,
    VIDEO,
    GENERIC,
}

private fun resolvePreviewType(node: FileNode): PreviewType {
    val name = node.name.lowercase()
    val mime = node.mimeType.orEmpty().lowercase()
    return when {
        mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".png") ||
            name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp") -> PreviewType.IMAGE
        mime.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".m4a") ||
            name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".wav") ||
            name.endsWith(".ogg") || name.endsWith(".opus") -> PreviewType.AUDIO
        mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") ||
            name.endsWith(".webm") || name.endsWith(".avi") || name.endsWith(".mov") ||
            name.endsWith(".3gp") -> PreviewType.VIDEO
        mime == "application/pdf" || name.endsWith(".pdf") -> PreviewType.PDF
        name.endsWith(".md") -> PreviewType.MARKDOWN
        name.endsWith(".zip") || mime.contains("zip") -> PreviewType.ARCHIVE
        mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") ||
            name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".xml") ||
            name.endsWith(".kt") || name.endsWith(".java") ||
            name.endsWith(".log") || name.endsWith(".gradle") || name.endsWith(".sh") ||
            name.endsWith(".py") || name.endsWith(".html") || name.endsWith(".css") ||
            name.endsWith(".js") || name.endsWith(".csv") -> PreviewType.TEXT
        else -> PreviewType.GENERIC
    }
}

/**
 * File preview (ATOMIC_UI_PLAN.md §7.5): a header with the name as stored and a mono size line,
 * then the viewer for the file type. Viewers keep their loading, error and paging states.
 */
@Composable
fun FilePreviewPane(
    node: FileNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? AtomicApp
    // Every preview counts as "opened" for Home > Recent (ALL_IN_ONE_PLAN.md 1.2).
    LaunchedEffect(node.id) {
        runCatching { app?.container?.recentsRepository?.recordOpened(node) }
    }
    val container = app?.container ?: return
    val colors = Atomic.colors
    val previewType = remember(node) { resolvePreviewType(node) }

    Column(modifier.background(colors.background).testTag("file_preview_pane")) {
        Row(
            Modifier.fillMaxWidth().padding(start = AtomicSpacing.s10, end = AtomicSpacing.s16, top = AtomicSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
        ) {
            AtomicIconButton(
                AtomicIcons.Close,
                "Close preview",
                onClick = onClose,
                variant = AtomicIconButtonVariant.Back,
                modifier = Modifier.testTag("preview_close"),
            )
            Column(Modifier.weight(1f)) {
                AtomicText(node.name, AtomicTextRole.Name, maxLines = 1)
                AtomicText(
                    "${FileUtils.formatBytes(node.size)} · ${FileUtils.formatDate(node.modifiedAt)}",
                    AtomicTextRole.MonoMeta,
                    maxLines = 1,
                )
            }
            if (onShare != null) {
                AtomicIconButton(
                    AtomicIcons.Share,
                    "Share file",
                    onClick = onShare,
                    modifier = Modifier.testTag("preview_share"),
                )
            }
            if (onOpenExternal != null) {
                AtomicIconButton(
                    AtomicIcons.OpenExternally,
                    "Open with another app",
                    onClick = onOpenExternal,
                    modifier = Modifier.testTag("preview_open_external"),
                )
            }
        }
        AtomicDivider(strong = true, modifier = Modifier.padding(top = AtomicSpacing.s4))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (previewType) {
                PreviewType.IMAGE -> ImagePreviewContent(node, container)
                PreviewType.PDF -> PdfPreviewContent(node, container)
                PreviewType.TEXT -> TextPreviewContent(node, container)
                PreviewType.MARKDOWN -> MarkdownPreviewContent(node, container)
                PreviewType.ARCHIVE -> ArchivePreviewContent(node, container)
                PreviewType.AUDIO -> AudioPreviewContent(node, container)
                PreviewType.VIDEO -> VideoPreviewContent(node, container)
                PreviewType.GENERIC -> GenericFileContent(node, container, onOpenExternal)
            }
        }
    }
}

@Composable
fun FilePreviewDialog(
    node: FileNode,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FilePreviewPane(
            node = node,
            onClose = onDismiss,
            modifier = modifier.fillMaxSize(),
            onShare = onShare,
            onOpenExternal = onOpenExternal,
        )
    }
}

/** Centred loading or error state shared by the viewers. */
@Composable
private fun ViewerState(
    loading: Boolean,
    error: String?,
) {
    Box(Modifier.fillMaxSize().padding(AtomicSpacing.s16), contentAlignment = Alignment.Center) {
        when {
            loading -> AtomicLoading("Loading preview…")
            error != null -> AtomicErrorState(title = "Can't preview this file", message = error)
        }
    }
}

@Composable
private fun ImagePreviewContent(
    node: FileNode,
    container: AppContainer,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(node.id) {
        isLoading = true
        when (val res = container.imagePreviewHelper.decodeImage(node.id)) {
            is FileResult.Success -> {
                bitmap = res.value
                isLoading = false
            }
            is FileResult.Failure -> {
                errorMessage = "The image may be damaged or in a format this phone can't decode."
                isLoading = false
            }
        }
    }

    val shown = bitmap
    if (isLoading || errorMessage != null || shown == null) {
        ViewerState(isLoading, errorMessage)
        return
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = node.name,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
        )
    }
}

@Composable
private fun PdfPreviewContent(
    node: FileNode,
    container: AppContainer,
) {
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(node.id) {
        isLoading = true
        when (val countRes = container.pdfPreviewHelper.getPageCount(node.id)) {
            is FileResult.Success -> {
                pageCount = countRes.value
                if (pageCount > 0) {
                    when (val pageRes = container.pdfPreviewHelper.renderPage(node.id, 0)) {
                        is FileResult.Success -> {
                            currentBitmap = pageRes.value
                            isLoading = false
                        }
                        is FileResult.Failure -> {
                            errorMessage = "The first page couldn't be drawn. Try opening it with another app."
                            isLoading = false
                        }
                    }
                } else {
                    errorMessage = "This PDF has no pages."
                    isLoading = false
                }
            }
            is FileResult.Failure -> {
                errorMessage = "The PDF couldn't be opened. It may be damaged or password-protected."
                isLoading = false
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    fun loadPage(pageIndex: Int) {
        if (pageIndex < 0 || pageIndex >= pageCount) return
        isLoading = true
        currentPage = pageIndex
        coroutineScope.launch {
            when (val pageRes = container.pdfPreviewHelper.renderPage(node.id, pageIndex)) {
                is FileResult.Success -> {
                    currentBitmap = pageRes.value
                    isLoading = false
                }
                is FileResult.Failure -> {
                    errorMessage = "Page ${pageIndex + 1} couldn't be drawn."
                    isLoading = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val page = currentBitmap
            if (isLoading || errorMessage != null || page == null) {
                ViewerState(isLoading, errorMessage)
            } else {
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = "PDF page ${currentPage + 1}",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (pageCount > 1) {
            AtomicDivider(strong = true)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s6),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtomicIconButton(
                    AtomicIcons.Back,
                    "Previous page",
                    onClick = { loadPage(currentPage - 1) },
                    enabled = currentPage > 0,
                )
                AtomicText("Page ${currentPage + 1} / $pageCount", AtomicTextRole.MonoLabel)
                AtomicIconButton(
                    AtomicIcons.Forward,
                    "Next page",
                    onClick = { loadPage(currentPage + 1) },
                    enabled = currentPage < pageCount - 1,
                )
            }
        }
    }
}

@Composable
private fun TextPreviewContent(
    node: FileNode,
    container: AppContainer,
) {
    var textContent by remember { mutableStateOf<TextContent?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wrapLines by remember { mutableStateOf(false) }

    LaunchedEffect(node.id) {
        isLoading = true
        when (val res = container.readFileContentUseCase.readText(node.id)) {
            is FileResult.Success -> {
                textContent = res.value
                isLoading = false
            }
            is FileResult.Failure -> {
                errorMessage = "The text couldn't be read. The file may be binary or unreadable."
                isLoading = false
            }
        }
    }

    val content = textContent
    if (isLoading || errorMessage != null || content == null) {
        ViewerState(isLoading, errorMessage)
        return
    }
    val colors = Atomic.colors

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = AtomicSpacing.s16, end = AtomicSpacing.s8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtomicText(
                "${content.totalLinesCount} lines" + if (content.isTruncated) " · first part shown" else "",
                AtomicTextRole.MonoMeta,
            )
            AtomicChip(label = "Wrap", selected = wrapLines, onSelectedChange = { wrapLines = it })
        }
        AtomicDivider()

        val scrollModifier =
            if (wrapLines) {
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
            }
        val mono = AtomicTypography.monoMeta

        Row(modifier = scrollModifier.padding(AtomicSpacing.s8)) {
            Column(
                modifier =
                    Modifier
                        .background(colors.surfaceInset, AtomicShape.sm)
                        .padding(horizontal = AtomicSpacing.s8, vertical = AtomicSpacing.s2),
            ) {
                content.lines.indices.forEach { index ->
                    Text(text = "${index + 1}", style = mono, color = colors.contentMuted)
                }
            }
            Spacer(modifier = Modifier.width(AtomicSpacing.s8))
            Column(modifier = Modifier.padding(vertical = AtomicSpacing.s2)) {
                content.lines.forEach { line ->
                    Text(text = line.ifEmpty { " " }, style = mono, color = colors.content)
                }
            }
        }
    }
}

@Composable
private fun ArchivePreviewContent(
    node: FileNode,
    container: AppContainer,
) {
    var entries by remember { mutableStateOf<List<ArchiveEntryInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(node.id) {
        isLoading = true
        when (val res = container.inspectArchiveUseCase(node.id)) {
            is FileResult.Success -> {
                entries = res.value
                isLoading = false
            }
            is FileResult.Failure -> {
                errorMessage = "The ZIP couldn't be read. It may be damaged or encrypted."
                isLoading = false
            }
        }
    }

    if (isLoading || errorMessage != null) {
        ViewerState(isLoading, errorMessage)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AtomicText(
            "${entries.size} items in archive",
            AtomicTextRole.MonoMeta,
            modifier = Modifier.padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s8),
        )
        AtomicDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.path }) { entry ->
                AtomicFileRow(
                    name = entry.path,
                    meta =
                        if (entry.isDirectory) {
                            "Folder"
                        } else {
                            "${FileUtils.formatBytes(entry.uncompressedSize)} · " +
                                "${FileUtils.formatBytes(entry.compressedSize)} packed"
                        },
                    icon = if (entry.isDirectory) AtomicIcons.Files else AtomicIcons.Document,
                    onClick = {},
                    modifier = Modifier.padding(horizontal = AtomicSpacing.s16),
                )
            }
        }
    }
}

@Composable
private fun GenericFileContent(
    node: FileNode,
    container: AppContainer,
    onOpenExternal: (() -> Unit)?,
) {
    var checksums by remember { mutableStateOf<FileChecksums?>(null) }
    var checksumError by remember { mutableStateOf<String?>(null) }
    var isCalculatingChecksums by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16),
    ) {
        AtomicText("No preview for this type. Details are below.", AtomicTextRole.BodySecondary)
        val hashes = checksums
        AtomicFactSheet(
            buildList {
                add(AtomicFact("Size", FileUtils.formatBytes(node.size), monoValue = true))
                add(AtomicFact("Type", node.mimeType ?: "Unknown", monoValue = true))
                add(AtomicFact("Modified", FileUtils.formatDate(node.modifiedAt), monoValue = true))
                if (hashes != null) {
                    add(AtomicFact("MD5", hashes.md5, monoValue = true))
                    add(AtomicFact("SHA-256", hashes.sha256, monoValue = true))
                }
            },
        )
        checksumError?.let { AtomicText(it, AtomicTextRole.BodySecondary, color = Atomic.colors.error) }
        if (hashes == null) {
            if (isCalculatingChecksums) {
                AtomicLoading("Calculating checksums…")
            } else {
                AtomicButton(
                    "Calculate checksums",
                    onClick = {
                        isCalculatingChecksums = true
                        checksumError = null
                        coroutineScope.launch {
                            when (val res = container.readFileContentUseCase.calculateChecksums(node.id)) {
                                is FileResult.Success -> checksums = res.value
                                is FileResult.Failure -> checksumError = "Checksums couldn't be calculated. Try again."
                            }
                            isCalculatingChecksums = false
                        }
                    },
                    variant = AtomicButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (onOpenExternal != null) {
            AtomicButton(
                "Open with another app",
                onClick = onOpenExternal,
                leadingIcon = AtomicIcons.OpenExternally,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 6f
