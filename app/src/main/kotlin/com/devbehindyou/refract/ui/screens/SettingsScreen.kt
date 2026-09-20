package com.devbehindyou.refract.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.DEFAULT_HIDDEN_FOLDER
import com.devbehindyou.refract.domain.model.HideMode
import com.devbehindyou.refract.domain.model.ThemeMode
import com.devbehindyou.refract.domain.model.normalizeHiddenFolder
import com.devbehindyou.refract.domain.repository.SettingsRepository
import com.devbehindyou.refract.ui.security.authenticate
import com.devbehindyou.refract.ui.security.canAuthenticate
import com.devbehindyou.refract.ui.security.findFragmentActivity

private class HideModeChoice(val mode: HideMode?, val title: String, val description: String)

private fun hideModeChoices(hiddenFolder: String) =
    listOf(
        HideModeChoice(null, "Ask every time", "Choose a method each time you hide a file."),
        HideModeChoice(
            HideMode.FAST_OBSCURE,
            "Fast Obscure",
            "The file stays in its folder. It is renamed to .refract_obscured and its header is masked. " +
                "This is not encryption.",
        ),
        HideModeChoice(
            HideMode.GALLERY,
            "Hide from Gallery",
            "The file moves to $hiddenFolder on its own storage, and photo apps ignore it.",
        ),
        HideModeChoice(
            HideMode.PRIVATE_STORAGE,
            "Refract Private Storage",
            "The file moves into Refract's private app folder. It is removed if Refract is uninstalled.",
        ),
    )

private val themeChoices =
    listOf(
        ThemeMode.SYSTEM to "Follow system",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark",
    )

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    hasStorageAccess: Boolean,
    onRequestStorageAccess: () -> Unit,
    onClearScanCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by settingsRepository.settings.collectAsState()
    var showFolderDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val version =
        remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        }

    // Changing the lock needs a successful unlock first, so someone holding an unlocked phone
    // cannot switch it off. Without any screen lock the lock cannot be enabled at all.
    fun setAuthRequired(enabled: Boolean) {
        val activity = context.findFragmentActivity()
        val available = canAuthenticate(context)
        when {
            activity == null -> Unit
            !available && enabled ->
                Toast.makeText(context, "Set up a screen lock, fingerprint or face unlock first", Toast.LENGTH_LONG)
                    .show()
            !available -> settingsRepository.update { it.copy(requireAuthForHidden = false) }
            else ->
                authenticate(activity, if (enabled) "Turn on the lock" else "Turn off the lock") { ok ->
                    if (ok) settingsRepository.update { it.copy(requireAuthForHidden = enabled) }
                }
        }
    }

    if (showFolderDialog) {
        HiddenFolderDialog(
            current = settings.hiddenFolder,
            onDismiss = { showFolderDialog = false },
            onSave = { folder ->
                settingsRepository.update { it.copy(hiddenFolder = folder) }
                showFolderDialog = false
            },
        )
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
                .testTag("settings_screen"),
    ) {
        SectionHeader("Appearance")
        themeChoices.forEach { (mode, label) ->
            ChoiceRow(
                title = label,
                description = null,
                selected = settings.themeMode == mode,
                onSelect = { settingsRepository.update { it.copy(themeMode = mode) } },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow(
                title = "Dynamic color",
                description = "Use colors from your wallpaper.",
                checked = settings.dynamicColor,
                onCheckedChange = { on -> settingsRepository.update { it.copy(dynamicColor = on) } },
            )
        }
        SectionDivider()

        SectionHeader("Browsing")
        SwitchRow(
            title = "Show hidden files",
            description = "Show files and folders whose names start with a dot.",
            checked = settings.showHiddenFiles,
            onCheckedChange = { on -> settingsRepository.update { it.copy(showHiddenFiles = on) } },
        )
        SectionDivider()

        SectionHeader("File hiding")
        Text(
            "Default method",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        hideModeChoices(settings.hiddenFolder).forEach { choice ->
            ChoiceRow(
                title = choice.title,
                description = choice.description,
                selected = settings.defaultHideMode == choice.mode,
                onSelect = { settingsRepository.update { it.copy(defaultHideMode = choice.mode) } },
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Hidden folder", style = MaterialTheme.typography.labelLarge)
            Text(
                "${settings.hiddenFolder} on each storage. Hide from Gallery puts files here. " +
                    "Fast Obscure keeps files where they are, and Private Storage uses Refract's own folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { showFolderDialog = true },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Change folder") }
        }
        SectionDivider()

        SectionHeader("Security")
        SwitchRow(
            title = "Lock hidden and private files",
            description = "Ask for your fingerprint, face or screen lock before opening them.",
            checked = settings.requireAuthForHidden,
            onCheckedChange = ::setAuthRequired,
        )
        SectionDivider()

        SectionHeader("Storage")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("All files access", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (hasStorageAccess) "Granted" else "Not granted. Refract cannot browse shared storage without it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasStorageAccess) {
                    OutlinedButton(onClick = onRequestStorageAccess) { Text("Manage access") }
                } else {
                    Button(onClick = onRequestStorageAccess) { Text("Grant access") }
                }
                OutlinedButton(
                    onClick = {
                        onClearScanCache()
                        Toast.makeText(context, "File scan cache cleared", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Clear scan cache") }
            }
        }
        SectionDivider()

        SectionHeader("About")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Refract File Manager", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Version ${version ?: "unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun ChoiceRow(
    title: String,
    description: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun HiddenFolderDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    val normalized = normalizeHiddenFolder(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden folder") },
        text = {
            Column {
                Text(
                    "A folder inside each storage, for example Refract/Hidden. It is created when you hide a file.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = normalized == null,
                    supportingText = {
                        if (normalized == null) Text("Enter a folder name without .. or a drive letter.")
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { normalized?.let(onSave) }, enabled = normalized != null) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { text = DEFAULT_HIDDEN_FOLDER }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
