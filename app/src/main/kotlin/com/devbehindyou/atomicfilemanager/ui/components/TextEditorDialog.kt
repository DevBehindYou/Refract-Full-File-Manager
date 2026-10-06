package com.devbehindyou.atomicfilemanager.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicDivider
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.organisms.AtomicSheet
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.EditableText
import com.devbehindyou.atomicfilemanager.domain.usecase.LineEnding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextCodec
import com.devbehindyou.atomicfilemanager.domain.usecase.TextEncoding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import kotlinx.coroutines.launch

/**
 * Plain text editor (ALL_IN_ONE_PLAN.md 2.3): monospace, line numbers while lines don't wrap,
 * find and replace. Saving keeps the file's encoding and line breaks and never writes in place
 * (see TextFileEditor). Closing with unsaved changes asks first. [onSaved] gets the saved file.
 */
@Composable
fun TextEditorDialog(
    node: FileNode,
    onDismiss: () -> Unit,
    onSaved: (FileNode) -> Unit = {},
) {
    val editor = (LocalContext.current.applicationContext as AtomicApp).container.textFileEditor
    val scope = rememberCoroutineScope()
    var loaded by remember(node.id) { mutableStateOf<FileResult<EditableText>?>(null) }
    var saved by remember(node.id) { mutableStateOf("") }
    var value by remember(node.id) { mutableStateOf(TextFieldValue("")) }
    var current by remember(node.id) { mutableStateOf(node) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(false) }

    LaunchedEffect(node.id) {
        val result = editor.open(node)
        if (result is FileResult.Success) {
            saved = result.value.text
            value = TextFieldValue(result.value.text)
        }
        loaded = result
    }
    val dirty = value.text != saved
    val close: () -> Unit = {
        if (dirty) confirmClose = true else onDismiss()
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = close)
        Column(
            Modifier.fillMaxSize().background(Atomic.colors.background).imePadding().testTag("text_editor"),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = AtomicSpacing.s10,
                    end = AtomicSpacing.s16,
                    top = AtomicSpacing.s4,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
            ) {
                AtomicIconButton(
                    AtomicIcons.Close,
                    "Close editor",
                    onClick = close,
                    variant = AtomicIconButtonVariant.Back,
                )
                Column(Modifier.weight(1f)) {
                    AtomicText(current.name, AtomicTextRole.Name, maxLines = 1)
                    val format = (loaded as? FileResult.Success)?.value?.format
                    AtomicText(status ?: EditorText.meta(format, dirty), AtomicTextRole.MonoMeta, maxLines = 1)
                }
                AtomicButton(
                    if (saving) "Saving…" else "Save",
                    onClick = {
                        val format = (loaded as? FileResult.Success)?.value?.format ?: return@AtomicButton
                        saving = true
                        val text = value.text
                        scope.launch {
                            when (val result = editor.save(current, text, format)) {
                                is FileResult.Success -> {
                                    saved = text
                                    current = result.value
                                    status = null
                                    onSaved(result.value)
                                }
                                is FileResult.Failure -> status = EditorText.saveFailed(result.error)
                            }
                            saving = false
                        }
                    },
                    enabled = dirty && !saving && loaded is FileResult.Success,
                    variant = AtomicButtonVariant.Solid,
                    modifier = Modifier.testTag("text_editor_save"),
                )
            }
            AtomicDivider(strong = true, modifier = Modifier.padding(top = AtomicSpacing.s4))

            when (val state = loaded) {
                null -> ViewerState(loading = true, error = null)
                is FileResult.Failure -> ViewerState(loading = false, error = EditorText.openFailed(state.error))
                is FileResult.Success -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s4),
                        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                    ) {
                        AtomicChip("Find", selected = findOpen, onSelectedChange = { findOpen = it })
                        AtomicChip("Wrap", selected = wrap, onSelectedChange = { wrap = it })
                    }
                    if (findOpen) FindBar(value, onChange = { value = it })
                    AtomicDivider()
                    EditorBody(value, wrap, onChange = { value = it }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (confirmClose) {
        AtomicSheet(label = "Unsaved changes", onDismiss = { confirmClose = false }) {
            AtomicText("Close without saving?", AtomicTextRole.DisplayPushed)
            AtomicText("Your changes to “${current.name}” will be lost.", AtomicTextRole.Body)
            AtomicButton(
                "Discard changes",
                onClick = {
                    confirmClose = false
                    onDismiss()
                },
                variant = AtomicButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth().testTag("text_editor_discard"),
            )
            AtomicButton(
                "Keep editing",
                onClick = { confirmClose = false },
                variant = AtomicButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditorBody(
    value: TextFieldValue,
    wrap: Boolean,
    onChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Atomic.colors
    val mono = AtomicTypography.monoMeta.copy(color = colors.content)
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val scroll =
        if (wrap) {
            Modifier.fillMaxSize().verticalScroll(vertical)
        } else {
            Modifier.fillMaxSize().verticalScroll(vertical).horizontalScroll(horizontal)
        }
    Box(modifier.fillMaxWidth()) {
        Row(scroll.padding(AtomicSpacing.s8)) {
            // Line numbers only line up while each line is one row, so they hide when wrapping.
            if (!wrap) {
                val lines = value.text.count { it == '\n' } + 1
                Text(
                    text = (1..lines).joinToString("\n"),
                    style = mono.copy(color = colors.contentMuted),
                    modifier = Modifier.background(colors.surfaceInset).padding(horizontal = AtomicSpacing.s6),
                )
                Spacer(Modifier.width(AtomicSpacing.s8))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = mono,
                cursorBrush = SolidColor(colors.content),
                modifier = Modifier.testTag("text_editor_field").then(if (wrap) Modifier.fillMaxWidth() else Modifier),
            )
        }
    }
}

@Composable
private fun FindBar(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    val matches = remember(value.text, query) { TextCodec.findAll(value.text, query) }
    val selected = matches.indexOfFirst { it.first == value.selection.min && it.last + 1 == value.selection.max }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s6),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            AtomicTextField(query, { query = it }, label = "Find", modifier = Modifier.weight(1f).testTag("find_query"))
            AtomicTextField(replacement, { replacement = it }, label = "Replace with", modifier = Modifier.weight(1f))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicText(
                EditorText.matches(selected, matches.size, query),
                AtomicTextRole.MonoLabel,
                modifier = Modifier.weight(1f),
            )
            AtomicButton(
                "Next",
                onClick = {
                    val next = matches.firstOrNull { it.first >= value.selection.max } ?: matches.first()
                    onChange(value.copy(selection = TextRange(next.first, next.last + 1)))
                },
                enabled = matches.isNotEmpty(),
                variant = AtomicButtonVariant.Text,
            )
            AtomicButton(
                "Replace all",
                onClick = {
                    val text = value.text.replace(query, replacement, ignoreCase = true)
                    onChange(TextFieldValue(text, TextRange(0)))
                },
                enabled = matches.isNotEmpty(),
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.testTag("find_replace_all"),
            )
        }
    }
}

/** Wording for [TextEditorDialog]; pure so it is unit-tested. */
internal object EditorText {
    fun meta(
        format: TextFormat?,
        dirty: Boolean,
    ): String {
        if (format == null) return "Opening…"
        val encoding =
            when (format.encoding) {
                TextEncoding.UTF8 -> "UTF-8"
                TextEncoding.UTF8_BOM -> "UTF-8 BOM"
                TextEncoding.UTF16LE -> "UTF-16 LE"
                TextEncoding.UTF16BE -> "UTF-16 BE"
                TextEncoding.LATIN1 -> "Latin-1"
            }
        val ending =
            when (format.lineEnding) {
                LineEnding.LF -> "LF"
                LineEnding.CRLF -> "CRLF"
                LineEnding.CR -> "CR"
            }
        return "$encoding · $ending" + if (dirty) " · unsaved" else ""
    }

    fun matches(
        selected: Int,
        count: Int,
        query: String,
    ): String =
        when {
            query.isEmpty() -> "Type to find"
            count == 0 -> "No matches"
            selected >= 0 -> "${selected + 1} of $count"
            count == 1 -> "1 match"
            else -> "$count matches"
        }

    fun openFailed(error: FileError): String =
        when (error) {
            is FileError.FileTooLarge -> "Files over ${error.limit / (1024 * 1024)} MB open read-only in the preview."
            is FileError.UnsupportedFormat -> "This file isn't plain text."
            else -> "The file couldn't be read."
        }

    fun saveFailed(error: FileError): String =
        when (error) {
            is FileError.DiskFull -> "Not saved · not enough space"
            is FileError.AccessDenied, is FileError.PermissionDenied, is FileError.ReadOnlyStorage ->
                "Not saved · this storage doesn't allow changes"
            else -> "Not saved · the original is unchanged"
        }
}
