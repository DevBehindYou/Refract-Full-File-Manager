package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSettingsRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicToggleCard
import com.devbehindyou.atomicfilemanager.data.backend.shizuku.ShizukuEntries
import com.devbehindyou.atomicfilemanager.data.backend.shizuku.ShizukuState
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId

/**
 * Labs on Storage (ALL_IN_ONE_PLAN.md 4.3): read other apps' `Android/data` and `Android/obb`
 * through Shizuku. Off by default; the risks are stated before the switch.
 */
@Composable
fun ShizukuLabsSection(onBrowseFolder: (FileNodeId) -> Unit) {
    val access = (LocalContext.current.applicationContext as? AtomicApp)?.container?.shizukuAccess ?: return
    var enabled by remember { mutableStateOf(access.enabled) }
    var state by remember { mutableStateOf(access.state()) }
    // Shizuku can be started or stopped while the app is in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver {
                    _,
                    event,
                ->
                if (event == Lifecycle.Event.ON_RESUME) state = access.state()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        AtomicSectionLabel("Labs")
        AtomicToggleCard(
            title = "Android/data and obb (Shizuku)",
            description = ShizukuText.WARNING,
            checked = enabled,
            onCheckedChange = {
                access.enabled = it
                enabled = it
                state = access.state()
            },
        )
        when (state) {
            ShizukuState.OFF -> Unit
            ShizukuState.NOT_RUNNING -> AtomicText(ShizukuText.NOT_RUNNING, AtomicTextRole.BodySecondary)
            ShizukuState.NEEDS_PERMISSION ->
                AtomicButton(
                    "Allow in Shizuku",
                    onClick = { access.requestPermission { state = access.state() } },
                    variant = AtomicButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth().testTag("shizuku_allow"),
                )
            ShizukuState.READY ->
                ShizukuEntries.roots().forEach { root ->
                    AtomicSettingsRow(
                        title = ShizukuText.title(root),
                        value = "Read only",
                        onClick = { onBrowseFolder(FileNodeId.shizuku(root)) },
                        modifier = Modifier.testTag("shizuku_root"),
                    )
                }
        }
    }
}

/** Wording for [ShizukuLabsSection]; pure so it is unit-tested. */
internal object ShizukuText {
    const val WARNING =
        "Reads other apps' private folders through Shizuku, which you install and start yourself. " +
            "Read only: you can browse and copy out, never change those apps' files. Off by default."
    const val NOT_RUNNING = "Shizuku isn't running. Install it and start it, then come back here."

    fun title(root: String): String = "Android/" + root.substringAfterLast('/')
}
