# Storage Access Flow

## 1. Backend selection at runtime

```mermaid
flowchart TD
    A[Need to access a node or volume] --> B{Node id prefix}
    B -->|"media:"| MS[MediaStoreBackend]
    B -->|"saf:"| SAF[SafBackend]
    B -->|"file:"| C{SDK_INT}

    C -->|"27–28"| FS[FileSystemBackend]
    C -->|"29"| D{Is it app-private?}
    D -->|yes| FS
    D -->|no| SAF2[SafBackend<br/>requires tree grant]

    C -->|"30+"| E{isExternalStorageManager?}
    E -->|true| F{Path under /Android/data or /obb?}
    F -->|yes| BLOCK[Blocked by platform<br/>explain honestly]
    F -->|no| FS
    E -->|false| G{Tree grant for this volume?}
    G -->|yes| SAF3[SafBackend]
    G -->|no| PROMPT[Prompt: All files access<br/>or choose a folder]
```

## 2. Volume discovery

```mermaid
flowchart LR
    SM[StorageManager.storageVolumes] --> MAP[Map to StorageVolumeInfo]
    MAP --> R1{"SDK ≥ 30?"}
    R1 -->|yes| DIR["StorageVolume.directory"]
    R1 -->|no| EFD["getExternalFilesDirs()<br/>walk up to volume root"]
    DIR & EFD --> PROBE[Probe read + probe write]
    PROBE --> FLAGS[Set AccessFlags + requiresGrant]
    FLAGS --> UI[Volume list]

    BR["ACTION_MEDIA_MOUNTED / UNMOUNTED / EJECT<br/>USB attach / detach"] --> REFRESH[Refresh volumes]
    REFRESH --> UI
    REFRESH --> CANCEL[Cancel operations on lost volumes]
```

## 3. What happens when a volume disappears mid-operation

```mermaid
sequenceDiagram
    participant OS as Android
    participant R as StorageRepository
    participant Q as OperationQueue
    participant EX as Executor
    participant UI as UI

    OS->>R: ACTION_MEDIA_EJECT (SD card)
    R->>R: mark volume unmounted
    R->>Q: volumeLost(volumeId)
    Q->>EX: cancel operations touching that volume
    EX->>EX: discard partial target if reachable
    EX->>Q: status = Failed(StorageUnavailable, summary)
    Q->>UI: operation card shows the failure + affected items
    R->>UI: if the user is inside that volume, pop to Browse with a message
```

No crash, no partial file with a final name, no silent loss.
