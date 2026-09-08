# DFD — File Browsing (Process 2.0 expanded)

```mermaid
flowchart TD
    USER([User])
    P11["1.0 Access management"]

    P21["2.1 Enumerate volumes"]
    P22["2.2 Select backend"]
    P23["2.3 List children"]
    P24["2.4 Filter hidden"]
    P25["2.5 Sort"]
    P26["2.6 Resolve MIME<br/>& type icons"]
    P27["2.7 Load thumbnails"]
    P28["2.8 Compute folder size<br/>(on demand)"]
    P29["2.9 Build breadcrumb"]

    D1[("D1 Device storage")]
    D2[("D2 MediaStore")]
    D3a[("D3.a Size cache")]
    D4[("D4 Preferences")]
    D5[("D5 Thumbnail cache")]

    USER -->|open app / Browse| P21
    P21 --> D1
    P21 -->|"StorageVolumeInfo[]"| USER
    P11 -->|access level| P22

    USER -->|tap folder / volume| P22
    P22 -->|"backend + node id"| P23
    P23 --> D1
    P23 --> D2
    P23 -->|"chunks of 200 FileNode"| P24
    D4 -->|showHidden| P24
    P24 --> P25
    D4 -->|SortSpec| P25
    P25 -->|"DirectoryState.Partial"| USER
    P25 --> P26
    P26 -->|"typed nodes"| USER
    P26 --> P27
    P27 <--> D5
    P27 --> D2
    P27 -->|"thumbnails for visible rows"| USER

    USER -->|"tap 'calculate size'"| P28
    P28 --> D1
    P28 <--> D3a
    P28 -->|size| USER

    P22 --> P29
    P29 -->|"ancestry from node id"| USER
```

## Timing

| Stage | Target | Note |
|---|---|---|
| 2.1 volumes | < 50 ms | Cached after first call, refreshed on mount events |
| 2.2 backend selection | < 1 ms | Pure decision, no I/O |
| 2.3 first chunk | < 80 ms | 200 entries |
| 2.4 + 2.5 | < 20 ms per chunk | On `Dispatchers.Default` |
| 2.6 MIME | < 5 ms | Extension map first; content sniffing only when unknown and the file is small |
| 2.7 thumbnails | < 120 ms per visible item | Off the critical path entirely |
| 2.8 folder size | unbounded, cancellable | Never on the critical path; user-triggered |
| 2.9 breadcrumb | < 5 ms | Derived from ancestry, not the back stack |

## Failure paths

```mermaid
flowchart TD
    A[2.3 List children] --> B{Result}
    B -->|"listFiles() returns null"| C["AccessDenied<br/>NOT an empty folder"]
    B -->|SecurityException| D[AccessDenied → offer re-grant]
    B -->|FileNotFoundException| E[FileNotFound → pop to parent]
    B -->|DeadObjectException| F[ProviderUnavailable → retry action]
    B -->|Volume unmounted| G[StorageUnavailable → pop to Browse]
    B -->|empty list| H[Genuine empty state]
```

The `null`-vs-empty distinction is the single most important error case in this diagram:
rendering "This folder is empty" when access was actually denied is the bug users report as
"the app deleted my files".
