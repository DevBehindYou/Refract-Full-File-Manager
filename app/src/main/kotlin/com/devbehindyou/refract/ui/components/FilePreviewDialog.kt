package com.devbehindyou.refract.ui.components

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devbehindyou.refract.RefractApp
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileResult
import com.devbehindyou.refract.domain.usecase.ArchiveEntryInfo
import com.devbehindyou.refract.domain.usecase.FileChecksums
import com.devbehindyou.refract.domain.usecase.TextContent
import com.devbehindyou.refract.ui.util.FileUtils
import kotlinx.coroutines.launch

private enum class PreviewType {
    IMAGE,
    PDF,
    TEXT,
    ARCHIVE,
    GENERIC,
}

private fun resolvePreviewType(node: FileNode): PreviewType {
    val name = node.name.lowercase()
    val mime = node.mimeType.orEmpty().lowercase()
    return when {
        mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp") -> PreviewType.IMAGE
        mime == "application/pdf" || name.endsWith(".pdf") -> PreviewType.PDF
        name.endsWith(".zip") || mime.contains("zip") -> PreviewType.ARCHIVE
        mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") ||
            name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".xml") ||
            name.endsWith(".kt") || name.endsWith(".java") || name.endsWith(".md") ||
            name.endsWith(".log") || name.endsWith(".gradle") || name.endsWith(".sh") ||
            name.endsWith(".py") || name.endsWith(".html") || name.endsWith(".css") ||
            name.endsWith(".js") || name.endsWith(".csv") -> PreviewType.TEXT
        else -> PreviewType.GENERIC
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewPane(
    node: FileNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? RefractApp
    val container = app?.container ?: return

    val previewType = remember(node) { resolvePreviewType(node) }

    Scaffold(
        modifier = modifier.testTag("file_preview_pane"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = node.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = FileUtils.formatBytes(node.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("preview_close")) {
                        Icon(Icons.Default.Close, contentDescription = "Close preview")
                    }
                },
                actions = {
                    if (onShare != null) {
                        IconButton(onClick = onShare, modifier = Modifier.testTag("preview_share")) {
                            Icon(Icons.Default.Share, contentDescription = "Share file")
                        }
                    }
                    if (onOpenExternal != null) {
                        IconButton(onClick = onOpenExternal, modifier = Modifier.testTag("preview_open_external")) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open with external app")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (previewType) {
                PreviewType.IMAGE -> ImagePreviewContent(node, container)
                PreviewType.PDF -> PdfPreviewContent(node, container)
                PreviewType.TEXT -> TextPreviewContent(node, container)
                PreviewType.ARCHIVE -> ArchivePreviewContent(node, container)
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
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

@Composable
private fun ImagePreviewContent(
    node: FileNode,
    container: com.devbehindyou.refract.AppContainer,
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
                errorMessage = "Failed to load image"
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator()
            errorMessage != null -> Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = node.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                )
            }
        }
    }
}

@Composable
private fun PdfPreviewContent(
    node: FileNode,
    container: com.devbehindyou.refract.AppContainer,
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
                            errorMessage = "Failed to render PDF page"
                            isLoading = false
                        }
                    }
                } else {
                    errorMessage = "PDF has no pages"
                    isLoading = false
                }
            }
            is FileResult.Failure -> {
                errorMessage = "Failed to open PDF"
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
                    errorMessage = "Failed to render page ${pageIndex + 1}"
                    isLoading = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                errorMessage != null -> Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                currentBitmap != null -> {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPage + 1}",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (pageCount > 1) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { loadPage(currentPage - 1) },
                        enabled = currentPage > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }

                    Text(
                        text = "Page ${currentPage + 1} of $pageCount",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    IconButton(
                        onClick = { loadPage(currentPage + 1) },
                        enabled = currentPage < pageCount - 1,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                }
            }
        }
    }
}

@Composable
private fun TextPreviewContent(
    node: FileNode,
    container: com.devbehindyou.refract.AppContainer,
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
                errorMessage = "Failed to load text content"
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val content = textContent ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${content.totalLinesCount} lines" + if (content.isTruncated) " (Truncated)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { wrapLines = !wrapLines }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.WrapText,
                        contentDescription = "Toggle line wrap",
                        tint = if (wrapLines) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val scrollState = rememberScrollState()
        val horizontalScrollState = rememberScrollState()

        val modifier = if (wrapLines) {
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        } else {
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .horizontalScroll(horizontalScrollState)
        }

        Row(
            modifier = modifier.padding(8.dp)
        ) {
            // Line numbers column
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                content.lines.indices.forEach { index ->
                    Text(
                        text = "${index + 1}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Code content column
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                content.lines.forEach { line ->
                    Text(
                        text = line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivePreviewContent(
    node: FileNode,
    container: com.devbehindyou.refract.AppContainer,
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
                errorMessage = "Failed to inspect ZIP archive"
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${entries.size} items in archive",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.path }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.FolderZip else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.path,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!entry.isDirectory) {
                            Text(
                                text = "${FileUtils.formatBytes(entry.uncompressedSize)} (Compressed: ${FileUtils.formatBytes(entry.compressedSize)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenericFileContent(
    node: FileNode,
    container: com.devbehindyou.refract.AppContainer,
    onOpenExternal: (() -> Unit)?,
) {
    var checksums by remember { mutableStateOf<FileChecksums?>(null) }
    var isCalculatingChecksums by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )

        Text(
            text = node.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataRow(label = "Size", value = FileUtils.formatBytes(node.size))
                MetadataRow(label = "Type", value = node.mimeType ?: "Unknown binary")
                MetadataRow(label = "Modified", value = FileUtils.formatDate(node.modifiedAt))

                if (checksums != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    MetadataRow(label = "MD5", value = checksums!!.md5)
                    MetadataRow(label = "SHA-256", value = checksums!!.sha256)
                }
            }
        }

        if (checksums == null) {
            OutlinedButton(
                onClick = {
                    isCalculatingChecksums = true
                    coroutineScope.launch {
                        when (val res = container.readFileContentUseCase.calculateChecksums(node.id)) {
                            is FileResult.Success -> {
                                checksums = res.value
                                isCalculatingChecksums = false
                            }
                            is FileResult.Failure -> {
                                isCalculatingChecksums = false
                            }
                        }
                    }
                },
                enabled = !isCalculatingChecksums,
            ) {
                if (isCalculatingChecksums) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Computing hashes...")
                } else {
                    Text("Calculate MD5 & SHA-256")
                }
            }
        }

        if (onOpenExternal != null) {
            Button(
                onClick = onOpenExternal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open with external app")
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (label.contains("MD5") || label.contains("SHA")) FontFamily.Monospace else FontFamily.Default,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
