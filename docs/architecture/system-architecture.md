# System Architecture Diagram

```mermaid
flowchart TB
    subgraph USER[" "]
        U([User])
    end

    subgraph APP["Refract — single process"]
        direction TB
        subgraph UIL["UI layer"]
            ACT[MainActivity<br/>edge-to-edge, single Activity]
            NAV[NavHost<br/>type-safe routes]
            SCR["Screens: Home, Browse, Search,<br/>Storage, Preview, Operations, Settings"]
            DS["Design system<br/>tokens · GlassSurface · LiquidBottomBar"]
            GCM[GlassCapabilityManager<br/>Tier A/B/C]
        end

        subgraph PRES["Presentation"]
            VM["ViewModels<br/>StateFlow · Channel effects"]
        end

        subgraph DOM["Domain — pure Kotlin"]
            UC["Use cases"]
            MOD["Models: FileNode, FileOperation,<br/>StorageVolumeInfo, FileError"]
            RIF["Repository interfaces"]
        end

        subgraph DAT["Data"]
            REP["Repository implementations"]
            SEL[StorageBackendSelector]
            FSB[FileSystemBackend]
            SAFB[SafBackend]
            MSB[MediaStoreBackend]
            SAM[StorageAccessManager<br/>+ PermissionManager]
            ARC[ArchiveRepository]
            OPQ[FileOperationQueue]
        end

        subgraph SVC["Background"]
            FGS["FileOperationService<br/>foreground, dataSync"]
            EXE[FileOperationExecutor]
            WM["WorkManager<br/>index · cache trim · scan"]
        end

        subgraph LOC["Local storage"]
            ROOM[(Room<br/>favorites · recents · operations<br/>trash · sizes · index)]
            DSP[(DataStore<br/>preferences)]
            CACHE[(Coil disk cache<br/>thumbnails)]
        end
    end

    subgraph AND["Android platform"]
        FILE["java.io.File"]
        SAFP["DocumentsContract<br/>SAF providers"]
        MS["MediaStore"]
        SM["StorageManager"]
        FP["FileProvider"]
        NOTIF["NotificationManager"]
    end

    subgraph EXT["Other apps"]
        VIEWER["Viewers, editors,<br/>share targets"]
    end

    U --> ACT --> NAV --> SCR
    SCR <--> DS
    DS <--> GCM
    SCR <--> VM
    VM --> UC --> RIF
    UC --> MOD
    REP -.implements.-> RIF
    VM -.enqueue.-> OPQ
    REP --> SEL
    SEL --> FSB & SAFB & MSB
    REP --> ROOM
    REP --> DSP
    REP --> ARC
    SAM --> SAFP
    VM --> SAM
    OPQ --> FGS --> EXE
    EXE --> SEL
    EXE --> ROOM
    FGS --> NOTIF
    WM --> REP
    FSB --> FILE
    SAFB --> SAFP
    MSB --> MS
    SEL --> SM
    SCR -.thumbnails.-> CACHE
    VM -.share/open.-> FP --> VIEWER
```

## Key properties this diagram encodes

1. **Only the data layer touches Android storage APIs.** No arrow crosses from UI or Domain
   to the platform box.
2. **Domain has no outgoing arrows** except to its own models. It is a JVM-testable island.
3. **The executor is reached through the queue, not directly.** A ViewModel cannot start a
   file operation synchronously — it can only enqueue one.
4. **Room is the source of truth for operation state**, which is what allows the queue to
   survive process death.
5. **`GlassCapabilityManager` is a UI-layer concern only.** Nothing below the design system
   knows the app has glass.
