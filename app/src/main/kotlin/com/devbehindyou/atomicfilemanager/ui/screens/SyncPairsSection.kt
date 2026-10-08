package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicFileRow
import com.devbehindyou.atomicfilemanager.core.designsystem.molecules.AtomicSectionLabel
import com.devbehindyou.atomicfilemanager.data.repository.SyncPairStore
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPair

/**
 * Saved folder syncs on Storage (ALL_IN_ONE_PLAN.md 4.1): a tap opens the comparison again with
 * the saved mode, and the plan is still shown before anything runs. Hidden until one is saved.
 */
@Composable
fun SyncPairsSection(onCompareFolders: (FileNodeId, FileNodeId) -> Unit) {
    val context = LocalContext.current
    val store = remember { SyncPairStore(context) }
    var pairs by remember { mutableStateOf(store.all()) }
    // Pairs are saved on the compare screen; reload when coming back to Storage.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver {
                    _,
                    event,
                ->
                if (event == Lifecycle.Event.ON_RESUME) pairs = store.all()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (pairs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
        AtomicSectionLabel("Saved folder syncs")
        Column {
            pairs.forEach { pair ->
                AtomicFileRow(
                    name = SyncPairText.title(pair),
                    meta = CompareText.mode(pair.mode),
                    icon = AtomicIcons.DualPane,
                    onClick = { onCompareFolders(FileNodeId(pair.leftRaw), FileNodeId(pair.rightRaw)) },
                    trailing = {
                        AtomicIconButton(
                            AtomicIcons.Close,
                            "Forget ${SyncPairText.title(pair)}",
                            onClick = {
                                store.remove(pair)
                                pairs = store.all()
                            },
                            variant = AtomicIconButtonVariant.Destructive,
                        )
                    },
                )
            }
        }
    }
}

/** Wording for saved pairs; pure so it is unit-tested. */
internal object SyncPairText {
    fun title(pair: SyncPair): String =
        "${CompareText.folderName(pair.leftRaw)} → ${CompareText.folderName(pair.rightRaw)}"
}
