package com.devbehindyou.refract

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.refract.data.volume.StorageVolumes
import com.devbehindyou.refract.domain.model.FileCategory
import com.devbehindyou.refract.domain.model.StorageType
import com.devbehindyou.refract.domain.model.StorageVolumeInfo
import com.devbehindyou.refract.ui.screens.BrowseScreen
import com.devbehindyou.refract.ui.screens.HomeScreen
import com.devbehindyou.refract.ui.screens.StorageScreen
import com.devbehindyou.refract.domain.model.FileNodeId
import com.devbehindyou.refract.ui.theme.RefractTheme

enum class NavigationTab(val title: String) {
    HOME("Home"),
    BROWSE("Browse"),
    STORAGE("Storage"),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            RefractTheme {
                RefractAppContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefractAppContent() {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    val defaultPath = Environment.getExternalStorageDirectory()?.absolutePath ?: context.filesDir.absolutePath
    var selectedFolderId by remember {
        mutableStateOf(FileNodeId.file(defaultPath))
    }

    var volumes by remember { mutableStateOf<List<StorageVolumeInfo>>(emptyList()) }
    var hasStorageAccess by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    fun checkAccess(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                val read = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                read
            }
        }.getOrDefault(false)
    }

    fun refreshVolumes() {
        hasStorageAccess = checkAccess()
        val enumerated = StorageVolumes.enumerate(context)
        volumes = if (enumerated.isNotEmpty()) {
            enumerated
        } else {
            val totalBytes = runCatching { context.filesDir.totalSpace }.getOrDefault(0L).coerceAtLeast(1L)
            val freeBytes = runCatching { context.filesDir.freeSpace }.getOrDefault(0L)
            listOf(
                StorageVolumeInfo(
                    id = "primary",
                    label = "Internal Shared Storage",
                    type = StorageType.INTERNAL_SHARED,
                    totalBytes = totalBytes,
                    freeBytes = freeBytes,
                    isRemovable = false,
                    isMounted = true,
                    rootNodeId = null,
                    requiresGrant = !hasStorageAccess,
                )
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshVolumes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshVolumes()
    }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        refreshVolumes()
    }

    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 600

    Scaffold(
        topBar = {
            if (currentTab != NavigationTab.BROWSE) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Refract",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showAboutDialog = true },
                            modifier = Modifier.testTag("about_button")
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "About Refract")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (!isExpanded) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.HOME,
                        onClick = { currentTab = NavigationTab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("tab_home")
                    )
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.BROWSE,
                        onClick = { currentTab = NavigationTab.BROWSE },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Browse") },
                        label = { Text("Browse") },
                        modifier = Modifier.testTag("tab_browse")
                    )
                    NavigationBarItem(
                        selected = currentTab == NavigationTab.STORAGE,
                        onClick = { currentTab = NavigationTab.STORAGE },
                        icon = { Icon(Icons.Default.Storage, contentDescription = "Storage") },
                        label = { Text("Storage") },
                        modifier = Modifier.testTag("tab_storage")
                    )
                }
            }
        }
    ) { innerPadding ->
        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavigationRail(
                    modifier = Modifier.testTag("nav_rail")
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.HOME,
                        onClick = { currentTab = NavigationTab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("tab_home")
                    )
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.BROWSE,
                        onClick = { currentTab = NavigationTab.BROWSE },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Browse") },
                        label = { Text("Browse") },
                        modifier = Modifier.testTag("tab_browse")
                    )
                    NavigationRailItem(
                        selected = currentTab == NavigationTab.STORAGE,
                        onClick = { currentTab = NavigationTab.STORAGE },
                        icon = { Icon(Icons.Default.Storage, contentDescription = "Storage") },
                        label = { Text("Storage") },
                        modifier = Modifier.testTag("tab_storage")
                    )
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenContent(
                        currentTab = currentTab,
                        volumes = volumes,
                        hasStorageAccess = hasStorageAccess,
                        selectedFolderId = selectedFolderId,
                        onFolderSelected = {
                            selectedFolderId = it
                            currentTab = NavigationTab.BROWSE
                        },
                        onNavigateBack = { currentTab = NavigationTab.HOME },
                        onRequestStorageAccess = { requestStorageAccess() },
                        context = context
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                MainScreenContent(
                    currentTab = currentTab,
                    volumes = volumes,
                    hasStorageAccess = hasStorageAccess,
                    selectedFolderId = selectedFolderId,
                    onFolderSelected = {
                        selectedFolderId = it
                        currentTab = NavigationTab.BROWSE
                    },
                    onNavigateBack = { currentTab = NavigationTab.HOME },
                    onRequestStorageAccess = { requestStorageAccess() },
                    context = context
                )
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Refract", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Refract File Manager",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Version 0.1.0 • Modern Android File Explorer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "An offline-first, private file manager built with Jetpack Compose and modern Android Storage APIs. Full access to internal, shared, and sandboxed storage.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun MainScreenContent(
    currentTab: NavigationTab,
    volumes: List<StorageVolumeInfo>,
    hasStorageAccess: Boolean,
    selectedFolderId: FileNodeId,
    onFolderSelected: (FileNodeId) -> Unit,
    onNavigateBack: () -> Unit,
    onRequestStorageAccess: () -> Unit,
    context: android.content.Context,
) {
    when (currentTab) {
        NavigationTab.HOME -> {
            HomeScreen(
                volumes = volumes,
                hasStorageAccess = hasStorageAccess,
                onNavigateToVolume = { volume ->
                    val defaultPath = context.filesDir.absolutePath
                    val targetPath = volume.rootNodeId ?: FileNodeId.file(
                        if (hasStorageAccess) (Environment.getExternalStorageDirectory()?.absolutePath ?: defaultPath)
                        else defaultPath
                    )
                    onFolderSelected(targetPath)
                },
                onNavigateToCategory = { category ->
                    val defaultPath = context.filesDir.absolutePath
                    val targetPath = when (category) {
                        FileCategory.DOWNLOAD -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                        FileCategory.IMAGE -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)?.absolutePath
                        FileCategory.AUDIO -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath
                        FileCategory.DOCUMENT -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.absolutePath
                        FileCategory.VIDEO -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.absolutePath
                        FileCategory.APK -> defaultPath
                        else -> if (hasStorageAccess) Environment.getExternalStorageDirectory()?.absolutePath else defaultPath
                    } ?: defaultPath
                    onFolderSelected(FileNodeId.file(targetPath))
                },
                onNavigateToFolder = onFolderSelected,
                onRequestStorageAccess = onRequestStorageAccess
            )
        }
        NavigationTab.BROWSE -> {
            BrowseScreen(
                initialFolderId = selectedFolderId,
                onNavigateBack = onNavigateBack
            )
        }
        NavigationTab.STORAGE -> {
            StorageScreen(
                volumes = volumes,
                onBrowseVolume = { volume ->
                    val defaultPath = context.filesDir.absolutePath
                    val targetPath = volume.rootNodeId ?: FileNodeId.file(
                        Environment.getExternalStorageDirectory()?.absolutePath ?: defaultPath
                    )
                    onFolderSelected(targetPath)
                },
                onBrowseFolder = onFolderSelected
            )
        }
    }
}

