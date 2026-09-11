package com.devbehindyou.refract.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.domain.model.HiddenItem
import com.devbehindyou.refract.domain.model.HideMode
import com.devbehindyou.refract.domain.repository.HiddenFilesRepository
import com.devbehindyou.refract.ui.util.FileUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenFilesScreen(
    repository: HiddenFilesRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenItems by repository.hiddenItems.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val tabs = listOf(
        "Fast Obscured" to HideMode.FAST_OBSCURE,
        "Hidden Gallery" to HideMode.GALLERY,
        "Private Storage" to HideMode.PRIVATE_STORAGE,
    )

    val currentMode = tabs[selectedTab].second
    val currentItems = hiddenItems.filter { it.mode == currentMode }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hidden Files") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentItems.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    for (item in currentItems) {
                                        when (item.mode) {
                                            HideMode.FAST_OBSCURE -> repository.restoreFastObscured(item)
                                            HideMode.GALLERY -> repository.unhideFromGallery(item)
                                            HideMode.PRIVATE_STORAGE -> {
                                                val parent = FileNodeId.file(item.originalLocation).raw.substringBeforeLast('/')
                                                repository.restoreFromPrivateStorage(item, FileNodeId.file(parent))
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("Unhide All")
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, (title, _) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            if (currentItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (currentMode) {
                                HideMode.FAST_OBSCURE -> Icons.Default.VisibilityOff
                                HideMode.GALLERY -> Icons.Default.Visibility
                                HideMode.PRIVATE_STORAGE -> Icons.Default.Lock
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        Text(
                            text = "No files in ${tabs[selectedTab].first}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(currentItems, key = { it.id }) { item ->
                        HiddenItemRow(
                            item = item,
                            onUnhide = {
                                scope.launch {
                                    when (item.mode) {
                                        HideMode.FAST_OBSCURE -> repository.restoreFastObscured(item)
                                        HideMode.GALLERY -> repository.unhideFromGallery(item)
                                        HideMode.PRIVATE_STORAGE -> {
                                            val parent = FileNodeId.file(item.originalLocation).raw.substringBeforeLast('/')
                                            repository.restoreFromPrivateStorage(item, FileNodeId.file(parent))
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    repository.deleteHiddenItem(item)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenItemRow(
    item: HiddenItem,
    onUnhide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.originalName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Original: ${item.originalLocation}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${FileUtils.formatBytes(item.size)} • ${FileUtils.formatDate(item.hiddenAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Row {
            IconButton(onClick = onUnhide) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Unhide file",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete permanently",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
