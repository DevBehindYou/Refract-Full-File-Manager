# DFD — File Operation (Process 4.0 expanded)

```mermaid
flowchart TD
    USER([User])
    OS([Android platform])

    P41["4.1 Validate request"]
    P42["4.2 Build + persist operation"]
    P43["4.3 Queue scheduling"]
    P44["4.4 Resolve collisions"]
    P45["4.5 Execute (stream I/O)"]
    P46["4.6 Report progress"]
    P47["4.7 Finalise + summarise"]
    P48["4.8 Undo"]
    P49["4.9 Trash management"]

    D1[("D1 Device storage")]
    D3d[("D3.d Operations")]
    D3e[("D3.e Trash records")]

    USER -->|"sources + destination + options"| P41
    P41 -->|"recursive? free space? names? access?"| P41
    P41 -->|rejected| USER
    P41 -->|valid| P42
    P42 --> D3d
    P42 --> P43
    P43 -->|"serial per volume, max 2 total"| P45
    P45 <--> D1
    P45 -->|collision| P44
    P44 -->|"ask"| USER
    USER -->|"overwrite / keep both / skip / apply-all"| P44
    P44 --> P45
    P45 --> P46
    P46 -->|"4 Hz"| D3d
    D3d -->|Flow| USER
    P46 -->|notification| OS
    P45 --> P47
    P47 -->|"summary + undo token"| D3d
    P47 -->|"Completed / Partially / Failed"| USER
    USER -->|undo| P48
    P48 --> D3d
    P48 -->|"inverse operation"| P42
    P45 -->|delete| P49
    P49 <--> D3e
    P49 --> D1
```

## Validation gate (4.1) — everything checked before anything happens

| Check | Failure |
|---|---|
| Sources non-empty | `NothingSelected` |
| Destination is not a source or a descendant of one | `InvalidDestination` |
| Destination is writable (`AccessFlags`) | `AccessDenied` |
| Free space > total × 1.02 | `DiskFull` |
| Names legal for the destination filesystem | sanitise + confirm |
| Not targeting `/Android/data` or `/obb` on API 30+ | `PlatformRestricted` |
| Destination volume mounted | `StorageUnavailable` |

## Execution loop (4.5)

```mermaid
flowchart LR
    A[Next item] --> B[Open input]
    B --> C[Open output]
    C --> D[Read 64–256KB]
    D --> E{ensureActive}
    E -->|cancelled| X[Discard partial → Cancelled]
    E -->|ok| F[Write]
    F --> G[bytesDone += n]
    G --> H{EOF?}
    H -->|no| D
    H -->|yes| I[flush + fsync]
    I --> J{size verified?}
    J -->|no| K[Discard partial → record failure]
    J -->|yes| L[Preserve mtime]
    L --> M{MOVE?}
    M -->|yes| N[Delete source]
    M -->|no| A
    N --> A
```

## Progress data (4.6)

```text
itemsDone / itemsTotal
bytesDone / bytesTotal          (bytesTotal may start unknown and resolve mid-run)
currentName
bytesPerSecond                  (rolling 3-second average, not instantaneous)
etaMillis                       (null until 5% or 3 seconds have elapsed)
```

ETA is deliberately withheld early — an ETA that says "4 hours" for two seconds and then
"12 seconds" is worse than no ETA.

## Trash (4.9)

```mermaid
flowchart TD
    A[Delete requested] --> B{useTrash AND same volume AND size < 500MB?}
    B -->|yes| C["Move to .trash/uuid/"]
    C --> D[Record original location in D3.e]
    D --> E[Undo = restore to original path]
    B -->|no| F[Stronger confirmation dialog]
    F --> G[Permanent unlink]
    G --> H[No undo]
    I["WorkManager: purge > 7 days or > 2GB"] --> J[Delete trash files + records]
```
