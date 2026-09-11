package com.devbehindyou.refract.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.HideMode

@Composable
fun HideFileDialog(
    node: FileNode,
    onConfirm: (HideMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedMode by remember { mutableStateOf(HideMode.FAST_OBSCURE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Hide \"${node.displayName}\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select how you would like to hide this file:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                HideOptionCard(
                    title = "Fast Obscure",
                    subtitle = "Masks file signature & header so apps cannot recognize it. Reversible instantly. (Not encryption).",
                    icon = Icons.Default.VisibilityOff,
                    selected = selectedMode == HideMode.FAST_OBSCURE,
                    onClick = { selectedMode = HideMode.FAST_OBSCURE },
                )

                Spacer(modifier = Modifier.height(10.dp))

                HideOptionCard(
                    title = "Hide from Gallery",
                    subtitle = "Moves media to a .nomedia folder so media scanner and photo apps ignore it.",
                    icon = Icons.Default.HideImage,
                    selected = selectedMode == HideMode.GALLERY,
                    onClick = { selectedMode = HideMode.GALLERY },
                )

                Spacer(modifier = Modifier.height(10.dp))

                HideOptionCard(
                    title = "Refract Private Storage",
                    subtitle = "Moves into Refract's private app sandbox. Inaccessible to other apps. Caution: removed if Refract is uninstalled.",
                    icon = Icons.Default.Lock,
                    selected = selectedMode == HideMode.PRIVATE_STORAGE,
                    onClick = { selectedMode = HideMode.PRIVATE_STORAGE },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMode) }) {
                Text("Hide File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun HideOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
