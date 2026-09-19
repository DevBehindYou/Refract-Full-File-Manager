package com.devbehindyou.refract.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileCategory
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.StorageType
import com.devbehindyou.refract.domain.model.StorageVolumeInfo
import com.devbehindyou.refract.ui.components.CategoryGrid
import com.devbehindyou.refract.ui.components.StorageOverviewCard
import com.devbehindyou.refract.ui.util.FileUtils

@Composable
fun HomeScreen(
    volumes: List<StorageVolumeInfo>,
    hasStorageAccess: Boolean,
    onNavigateToVolume: (StorageVolumeInfo) -> Unit,
    onNavigateToCategory: (FileCategory) -> Unit,
    onNavigateToFolder: (FileNodeId) -> Unit,
    onRequestStorageAccess: () -> Unit,
    onOpenPrivateFiles: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val primaryVolume = volumes.firstOrNull { it.type == StorageType.INTERNAL_SHARED } ?: volumes.firstOrNull()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("home_screen"),
    ) {
        if (!hasStorageAccess) {
            PermissionCard(
                onGrantClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent =
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    } else {
                        onRequestStorageAccess()
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Storage Overview Card
        StorageOverviewCard(
            volume = primaryVolume,
            onClick = {
                primaryVolume?.let { onNavigateToVolume(it) }
            },
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Categories Grid
        CategoryGrid(
            onCategoryClick = onNavigateToCategory,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Storage Volumes Section
        Text(
            text = "Storage Locations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        volumes.forEach { volume ->
            VolumeItem(
                volume = volume,
                onClick = { onNavigateToVolume(volume) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Always show App Private storage option
        AppPrivateStorageItem(
            onClick = onOpenPrivateFiles,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Access Directories
        Text(
            text = "Quick Access",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        QuickAccessFolders(
            onFolderClick = onNavigateToFolder,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionCard(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("permission_warning_card"),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "All Files Access Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "To browse and manage your photos, downloads, and other shared files on this device, " +
                        "Refract requires All Files Access permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Grant Access")
            }
        }
    }
}

@Composable
private fun VolumeItem(
    volume: StorageVolumeInfo,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("volume_item_${volume.id}"),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = volume.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${FileUtils.formatBytes(
                        volume.freeBytes,
                    )} free of ${FileUtils.formatBytes(volume.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Open ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AppPrivateStorageItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("app_private_storage_item"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FolderSpecial,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Private files",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Files moved into private storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Open ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun QuickAccessFolders(onFolderClick: (FileNodeId) -> Unit) {
    val context = LocalContext.current
    val fallback = FileNodeId.file(context.filesDir.absolutePath)
    val quickFolders =
        listOf(
            "Downloads" to (
                runCatching {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath?.let {
                        FileNodeId.file(it)
                    }
                }.getOrNull() ?: fallback
            ),
            "DCIM" to (
                runCatching {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.absolutePath?.let {
                        FileNodeId.file(it)
                    }
                }.getOrNull() ?: fallback
            ),
            "Documents" to (
                runCatching {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.absolutePath?.let {
                        FileNodeId.file(it)
                    }
                }.getOrNull() ?: fallback
            ),
            "Pictures" to (
                runCatching {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)?.absolutePath?.let {
                        FileNodeId.file(it)
                    }
                }.getOrNull() ?: fallback
            ),
            "Music" to (
                runCatching {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath?.let {
                        FileNodeId.file(it)
                    }
                }.getOrNull() ?: fallback
            ),
        )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        quickFolders.forEach { (name, id) ->
            Card(
                onClick = { onFolderClick(id) },
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
