package com.devbehindyou.refract.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devbehindyou.refract.data.volume.PhoneFileIndex
import com.devbehindyou.refract.data.volume.PhoneIndexSnapshot
import com.devbehindyou.refract.domain.model.FileCollection
import com.devbehindyou.refract.domain.model.FileNode
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.ui.components.FilePreviewDialog
import com.devbehindyou.refract.ui.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files of one collection plus the snapshot they were computed from. */
private class CollectionResult(val source: PhoneIndexSnapshot?, val files: List<FileNode>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    collection: FileCollection,
    roots: List<FileNodeId>,
    index: PhoneFileIndex,
    onBack: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var snapshot by remember { mutableStateOf(PhoneIndexSnapshot()) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by rememberSaveable(collection) { mutableStateOf("") }
    var preview by remember { mutableStateOf<FileNode?>(null) }
    LaunchedEffect(roots, refresh) {
        snapshot = PhoneIndexSnapshot()
        index.scan(roots, refresh > 0).collect { snapshot = it }
    }
    // Filtering and sorting every file on the phone must not run on the main thread. The producer restarts
    // (and cancels the previous run) whenever a newer scan snapshot, collection or query arrives.
    val result by produceState(CollectionResult(null, emptyList()), snapshot, collection, query) {
        val source = snapshot
        val files =
            withContext(Dispatchers.Default) {
                source.files
                    .filter { collection.matches(it) && it.name.contains(query, ignoreCase = true) }
                    .sortedByDescending { it.modifiedAt }
            }
        value = CollectionResult(source, files)
    }
    val files = result.files
    val settled = result.source === snapshot
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh category") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (roots.isEmpty()) {
                Text("Allow storage access to find files on your phone and SD card.", Modifier.padding(16.dp))
                Button(onClick = onGrantAccess, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Grant access")
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search ${collection.title.lowercase()}") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                val scanning = if (snapshot.complete) "" else " • Scanning…"
                Text(
                    "${files.size} files • Internal storage and SD card$scanning",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!snapshot.complete) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (snapshot.unreadable > 0) {
                    Text(
                        "Some protected or unavailable folders could not be read.",
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (snapshot.complete && settled && files.isEmpty()) {
                    Text("No matching files found.", Modifier.padding(24.dp))
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(files, key = { it.id.raw }) { file ->
                        ListItem(
                            headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                val folder = file.parentId?.raw?.removePrefix(FileNodeId.Prefix.FILE.scheme).orEmpty()
                                Text(
                                    "${FileUtils.formatBytes(file.size)} • $folder",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Description, null) },
                            modifier = Modifier.clickable { preview = file },
                        )
                    }
                }
            }
        }
    }
    preview?.let { FilePreviewDialog(node = it, onDismiss = { preview = null }) }
}
