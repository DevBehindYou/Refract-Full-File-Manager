# Functional Requirements

Every requirement is numbered, testable, and tagged with its release (`MVP`, `V1`, `FUT`)
and its priority (`M` must, `S` should, `C` could).

---

## FR-1 Storage access and volumes

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-1.1 | The app enumerates storage volumes via `StorageManager`, never by hardcoded path | MVP | M |
| FR-1.2 | Primary shared storage is browsable on every supported API level | MVP | M |
| FR-1.3 | SD card is browsable when present; write requires a persisted SAF tree on API 29+ | MVP | M |
| FR-1.4 | Volume mount/unmount is detected and the UI updates within 2 s without crashing | MVP | M |
| FR-1.5 | USB OTG volumes are detected, browsable, and safely removed mid-operation | V1 | S |
| FR-1.6 | App-private directories are reachable via a "This app's files" entry | V1 | C |
| FR-1.7 | When a volume disappears during navigation, the user is returned to the volume list with an explanatory message | MVP | M |

## FR-2 Permissions

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-2.1 | An in-app rationale screen precedes every system permission request | MVP | M |
| FR-2.2 | API 27–28: request `READ_/WRITE_EXTERNAL_STORAGE` | MVP | M |
| FR-2.3 | API 29: request read permission and drive access through SAF tree grants | MVP | M |
| FR-2.4 | API 30+: offer All Files Access with a clear explanation; SAF mode is a complete fallback | MVP | M |
| FR-2.5 | API 33+: request granular media permissions when All Files Access is absent | MVP | M |
| FR-2.6 | API 33+: request `POST_NOTIFICATIONS` before the first background operation, not at launch | MVP | M |
| FR-2.7 | SAF tree grants are persisted with `takePersistableUriPermission` and survive reboot | MVP | M |
| FR-2.8 | The app remains usable with zero optional permissions granted (app-private files only) | MVP | M |
| FR-2.9 | Permission revocation while running is detected and surfaced without a crash | MVP | M |
| FR-2.10 | A troubleshooting screen explains exactly which access is missing and how to grant it | MVP | S |

## FR-3 Browsing

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-3.1 | Directory contents list with name, icon/thumbnail, size, modified date | MVP | M |
| FR-3.2 | Folders sort before files by default | MVP | M |
| FR-3.3 | Sort by name, size, date, type; ascending/descending; persisted per folder | MVP | M |
| FR-3.4 | List and grid view modes, persisted globally | MVP | M |
| FR-3.5 | Hidden files toggle (dotfiles), off by default, persisted | MVP | M |
| FR-3.6 | Breadcrumb showing the path, each segment tappable, horizontally scrollable | MVP | M |
| FR-3.7 | Pull to refresh re-reads the current directory | MVP | S |
| FR-3.8 | Directories with 10,000+ entries load progressively without ANR | MVP | M |
| FR-3.9 | Folder size is computed on demand, in the background, and cached | MVP | S |
| FR-3.10 | Back returns up one directory level; back at a volume root returns to Browse | MVP | M |
| FR-3.11 | Folder history (recently visited locations) is available | V1 | C |
| FR-3.12 | Dual-pane browsing on windows ≥ 840dp wide | V1 | S |

## FR-4 File operations

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-4.1 | Copy one or many items to a chosen destination | MVP | M |
| FR-4.2 | Move one or many items; same-volume moves use rename where possible | MVP | M |
| FR-4.3 | Rename a single item with invalid-character validation | MVP | M |
| FR-4.4 | Delete to app trash where supported; permanent delete requires explicit confirmation | MVP | M |
| FR-4.5 | Create folder, create empty file | MVP | M |
| FR-4.6 | Share via `Intent.ACTION_SEND` / `SEND_MULTIPLE` using `FileProvider` | MVP | M |
| FR-4.7 | Open with, using MIME resolution and a chooser | MVP | M |
| FR-4.8 | Name collisions offer: overwrite, keep both (auto-rename), skip, and apply-to-all | MVP | M |
| FR-4.9 | Operations show live progress: current file, item n of m, bytes, ETA | MVP | M |
| FR-4.10 | Operations are cancellable at any point; partial results are cleaned up or clearly reported | MVP | M |
| FR-4.11 | Operations continue when the app is backgrounded, via a foreground service | MVP | M |
| FR-4.12 | Failures on individual items do not abort the batch; a summary lists failures | MVP | M |
| FR-4.13 | An operation queue screen lists active and recent operations | MVP | S |
| FR-4.14 | Undo is offered for move, rename and trash-delete for 10 s via snackbar and 24 h in the queue | V1 | S |
| FR-4.15 | Pause and resume for copy/move | V1 | C |
| FR-4.16 | A clipboard holds cut/copied items across navigation until cleared | V1 | S |

## FR-5 Search

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-5.1 | Search by filename substring, case-insensitive, with results streaming as found | MVP | M |
| FR-5.2 | Scope selector: current folder tree / all storage / a specific volume | MVP | M |
| FR-5.3 | Filters: category, extension, size range, modified date range | MVP | M |
| FR-5.4 | First results appear within 300 ms for indexed sources | MVP | M |
| FR-5.5 | Search is cancellable and cancels immediately on query change | MVP | M |
| FR-5.6 | Recent searches are stored locally and clearable | MVP | S |
| FR-5.7 | Results show the containing folder and allow jumping to it | MVP | M |
| FR-5.8 | Indexed search over a Room database with incremental refresh | V1 | S |
| FR-5.9 | Content search inside text files | FUT | C |

## FR-6 Categories, recents, favourites

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-6.1 | Categories: Images, Video, Audio, Documents, Archives, APKs, Downloads | MVP | M |
| FR-6.2 | Category contents come from MediaStore where possible, filesystem otherwise | MVP | M |
| FR-6.3 | Recent files: modified in the last 7/30 days, sortable | MVP | M |
| FR-6.4 | Favourite/pin a file or folder; pinned items appear on Home | MVP | M |
| FR-6.5 | Favourites survive reinstall-safe storage (Room) and handle missing targets gracefully | MVP | M |
| FR-6.6 | Large files view (> 100 MB, configurable) | V1 | S |
| FR-6.7 | Empty folders view with bulk delete | V1 | C |
| FR-6.8 | Duplicate finder with safe-selection assistance (never auto-selects all copies) | V1 | S |

## FR-7 Storage analysis

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-7.1 | Per-volume used/free/total with a single clear meter | MVP | M |
| FR-7.2 | Breakdown by category with sizes and proportions | MVP | M |
| FR-7.3 | Largest folders (top 20) and largest files (top 50) | MVP | M |
| FR-7.4 | Scanning is incremental, cancellable, and reports progress | MVP | M |
| FR-7.5 | Results are cached with a visible "last scanned" timestamp | MVP | S |
| FR-7.6 | A cleanup flow suggests candidates but never deletes without per-item confirmation | V1 | S |

## FR-8 Preview

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-8.1 | Image viewer: pan, zoom, swipe between images in the folder | MVP | M |
| FR-8.2 | Video preview via Media3 with play/pause/seek | MVP | M |
| FR-8.3 | Audio preview with metadata and album art | MVP | M |
| FR-8.4 | Text/code/markdown/json preview with streaming load and a size cap (2 MB, then "open externally") | MVP | M |
| FR-8.5 | Unsupported types show file info plus "Open with" | MVP | M |
| FR-8.6 | PDF preview via `PdfRenderer` | V1 | S |
| FR-8.7 | APK metadata (label, package, version, permissions count, icon) | V1 | S |
| FR-8.8 | File info sheet: name, path, size, type, dates, permissions, checksum on demand | MVP | M |

## FR-9 Archives

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-9.1 | Browse the contents of a ZIP without extracting | MVP | S |
| FR-9.2 | Extract a ZIP wholly or selectively, into a chosen destination | MVP | M |
| FR-9.3 | Create a ZIP from a selection, with a compression level choice | MVP | M |
| FR-9.4 | Path traversal ("Zip Slip") entries are rejected and reported | MVP | M |
| FR-9.5 | Corrupt archives fail with a clear message, never a crash | MVP | M |
| FR-9.6 | TAR / TAR.GZ support | V1 | C |
| FR-9.7 | Password-protected ZIP reading | V1 | C |
| FR-9.8 | 7z support | FUT | C |

## FR-10 Interface, theming and accessibility

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-10.1 | Light and dark themes, following system by default, overridable | MVP | M |
| FR-10.2 | Dynamic colour (Material You) on API 31+, opt-out available | MVP | S |
| FR-10.3 | Liquid Glass tier auto-selected, with a manual override (Auto/High/Medium/Off) | MVP | M |
| FR-10.4 | Full TalkBack support with meaningful labels on every interactive element | MVP | M |
| FR-10.5 | Layout intact at font scale 200% and display size largest | MVP | M |
| FR-10.6 | Reduce motion and reduce transparency respected system-wide | MVP | M |
| FR-10.7 | All touch targets ≥ 48dp | MVP | M |
| FR-10.8 | Keyboard and D-pad navigation across all screens | V1 | S |
| FR-10.9 | Adaptive layouts for compact/medium/expanded window classes | V1 | S |
| FR-10.10 | Command palette reachable by keyboard shortcut and from the toolbar | V1 | C |

## FR-11 Settings and diagnostics

| ID | Req | Rel | Pri |
|---|---|---|---|
| FR-11.1 | Settings: appearance, glass tier, default view, sort, hidden files, confirmations, storage access, about | MVP | M |
| FR-11.2 | "Storage access" section shows granted permissions and links to fix each | MVP | M |
| FR-11.3 | Clear caches (thumbnails, index) from Settings | MVP | S |
| FR-11.4 | Crash reporting is opt-in and off by default | MVP | M |
| FR-11.5 | An in-app log viewer for the last 200 operation events, exportable and redacted | V1 | C |
