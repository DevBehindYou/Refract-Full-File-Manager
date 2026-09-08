# DFD Level 0 — Context Diagram

The whole app as one process, with its external entities and data stores.

```mermaid
flowchart TD
    USER([External entity:<br/>User])
    OTHER([External entity:<br/>Other apps])
    OS([External entity:<br/>Android platform])

    P0["Process 0<br/>Refract File Manager"]

    D1[("D1 · Device storage<br/>internal · SD · USB")]
    D2[("D2 · MediaStore index")]
    D3[("D3 · App database<br/>Room")]
    D4[("D4 · App preferences<br/>DataStore")]
    D5[("D5 · Thumbnail cache")]

    USER -->|"browse, search, select,<br/>operation commands"| P0
    P0 -->|"file lists, previews, progress,<br/>storage insights, errors"| USER

    P0 <-->|"read/write files,<br/>enumerate volumes"| D1
    P0 -->|"query media, categories,<br/>recent, sizes"| D2
    D2 -->|"indexed metadata"| P0
    P0 <-->|"favourites, recents, operations,<br/>trash, size cache, index"| D3
    P0 <-->|"settings"| D4
    P0 <-->|"thumbnails"| D5

    P0 -->|"content:// URIs via FileProvider<br/>(share, open with)"| OTHER
    OTHER -->|"ACTION_VIEW / SEND<br/>content:// URIs"| P0

    P0 -->|"permission requests,<br/>foreground service, notifications"| OS
    OS -->|"grants, mount events,<br/>thermal/battery state, config changes"| P0
```

## External entities

| Entity | Gives Refract | Receives from Refract |
|---|---|---|
| **User** | Navigation, selections, queries, operation commands, permission decisions | File listings, previews, progress, storage analysis, errors |
| **Other apps** | Incoming view/share intents with `content://` URIs | Outgoing `content://` URIs with least-privilege grants |
| **Android platform** | Permission results, storage mount events, thermal/battery/accessibility state | Permission requests, service lifecycle, notifications |

## Data stores

| Store | Owner | Persistence |
|---|---|---|
| **D1 Device storage** | The user | Permanent — the thing being managed |
| **D2 MediaStore** | Android | System-owned index; read-mostly for us |
| **D3 Room** | Refract | App-private; survives restart, cleared on uninstall |
| **D4 DataStore** | Refract | App-private settings |
| **D5 Thumbnail cache** | Refract (Coil) | App cache dir; evictable by the system |

## Trust boundaries

* **D1 content is untrusted.** Filenames, archive entries and file contents come from the
  outside world and are validated (`architecture/SECURITY.md`).
* **Incoming intents are untrusted.** URIs are validated; paths from external callers are
  never resolved directly.
* **D3/D4/D5 are app-private** and never contain file contents.
* **Nothing crosses a network boundary.** There is no network external entity on this
  diagram, and that is the whole point of `PRIVACY.md`.
