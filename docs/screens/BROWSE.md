# Screen — Browse and Folder Browser

Browse is the volume list. The folder browser is a recursive destination. They share one
component set and one ViewModel shape.

## 1. Purpose

Give the filesystem to people who want it, with a path always visible and never more than one
tap from any ancestor.

## 2. Browse (volume list)

```text
┌────────────────────────────────────────┐
│  Browse                          ⋮     │
├────────────────────────────────────────┤
│  📱 Internal storage                   │
│     43.8 GB free of 128 GB        ›    │
│  💾 SD card                            │
│     12.1 GB free of 64 GB         ›    │
│  🔌 USB drive          Tap to allow ›  │
│  📦 App files                     ›    │
├────────────────────────────────────────┤
│  Quick access                          │
│  ▸ Downloads    ▸ DCIM    ▸ Documents  │
└────────────────────────────────────────┘
```

* Volumes come from `StorageManager`; a volume needing a grant is **shown**, not hidden.
* "App files" exposes app-private storage and is always available regardless of permission.

## 3. Folder browser

```text
┌────────────────────────────────────────┐
│  ‹  Download                    ⋮      │  ← GlassToolbar
│  📱 › Internal › Download              │  ← GlassBreadcrumb (scrollable)
│  [ Name ▾ ]  [ ▤ ]                     │  ← sort + view mode
├────────────────────────────────────────┤
│  📁 Invoices              12 items  ›  │
│  📁 Music                  4 items  ›  │
│  📄 report.pdf      1.2 MB · today  ⋮  │
│  🖼 IMG_4821.jpg    3.4 MB · today  ⋮  │
│  📦 backup.zip      112 MB · 12 Mar ⋮  │
├────────────────────────────────────────┤
│                             (+)        │  ← GlassFab: new folder / new file
│    ⌂     ▤     🔍     ◷     ⋯          │
└────────────────────────────────────────┘
```

## 4. UI state

```kotlin
data class BrowseUiState(
    val nodeId: FileNodeId? = null,
    val path: List<BreadcrumbSegment> = emptyList(),
    val nodes: List<FileNodeUi> = emptyList(),
    val loadState: ListLoadState = ListLoadState.Loading,
    val sort: SortSpec = SortSpec.default,
    val viewMode: ViewMode = ViewMode.LIST,
    val showHidden: Boolean = false,
    val selection: Set<FileNodeId> = emptySet(),
    val selectionMode: Boolean = false,
    val clipboardCount: Int = 0,
    val requiresGrant: Boolean = false,
    val activeOperationsHere: Int = 0,
)

sealed interface ListLoadState { Loading; Partial(loaded: Int); Complete; Failed(FileError) }
```

## 5. Interactions

| Action | Result |
|---|---|
| Tap folder | Navigate into it (new `FolderRoute` entry) |
| Tap file | → Preview |
| Long press | Enter selection mode with that item selected |
| Tap row overflow | Context menu: Open, Open with, Share, Copy, Move, Rename, Delete, Add to favourites, File info |
| Tap breadcrumb segment | Navigate directly to that ancestor |
| Tap `…` in an overflowing breadcrumb | Menu of hidden ancestors |
| Sort chip | Sort sheet: Name / Size / Date / Type, asc/desc, folders-first toggle |
| View chip | Toggle list ↔ grid |
| Toolbar overflow | Select, Show hidden files, New folder, New file, Paste (if clipboard non-empty), Properties |
| FAB | New folder (long press fans out New file) |
| Pull down | Refresh |
| Back | Up one level; from a volume root → Browse |
| Drag a row onto a folder (V1) | Move |

## 6. Animations

* Forward navigation: incoming slides from +8% X and scales 0.98 → 1.0; outgoing slides to
  −4% and dims. Predictive back scrubs the reverse.
* Rows compress to 0.995 on press.
* Breadcrumb: new segment slides in from the trailing edge and auto-scrolls into view.
* Selection entry: staggered checkbox slide-in over visible rows.
* Sort change: `animateItemPlacement()` on the list so rows glide to their new positions
  (capped — disabled above 500 items, where it costs more than it communicates).

## 7. Edge cases

| Case | Behaviour |
|---|---|
| 10,000+ items | `Partial` state paints the first 200 within ~80 ms; a subtle "loading more" footer until complete |
| `listFiles()` returns null | **`AccessDenied` error state**, never an empty state |
| Folder deleted while open | `FileNotFound` → pop to parent with a message |
| Volume unmounted while open | Pop to Browse with a message; cancel operations on that volume |
| Permission revoked while open | Permission card replaces the list; a Grant action restores it |
| Hidden files toggled | List re-filters without a re-read |
| Symlink loop | Depth cap at 32; the branch stops with a note |
| Empty folder | Empty state with a Create folder action |
| Operation running in this folder | Affected rows show a pending state and are not selectable |
| Very long file name | Middle ellipsis; full name in the info sheet and to TalkBack |
| Grid mode with no thumbnails available | Type icons at grid size, no layout shift |

## 8. Accessibility

* Breadcrumb: each segment is a button ("Navigate to Download"); the last is a heading and
  not clickable.
* Rows carry custom actions (Copy / Move / Delete / Share / Info) so nothing needs long press.
* Sort and view chips announce their current value ("Sort by name, ascending").
* Entering selection mode is announced once; subsequent changes announce only the count.
* The FAB is last in traversal order, after the list.

## 9. Responsive

| Class | Layout |
|---|---|
| Compact | Single pane, floating bottom bar |
| Medium | Navigation rail; grid gains a column; breadcrumb gets more room before overflowing |
| Expanded | **Dual pane**: folder list left (360dp), preview/info right. Selecting a folder in the left pane navigates *within* the left pane; selecting a file fills the right pane |
| ≥ 1200dp | Optional third leading pane: persistent folder tree |

## 10. Performance

* Stable keys (`node.id.raw`) and `contentType` on every item.
* Thumbnails only for visible rows; type icons are instant.
* Folder sizes are **not** computed during listing — `childCount` only, and only when cheap.
* Sorting on `Dispatchers.Default`; `animateItemPlacement` disabled above 500 items.
* Scroll position saved per `nodeId` so returning up the tree lands where you left.
