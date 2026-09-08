# Screen — Home

## 1. Purpose

Answer three questions in the first second: *how full is my phone*, *where are my files*,
*what did I touch recently*. It is the default destination and the only screen a beginner
may ever use.

## 2. Layout (compact)

```text
┌────────────────────────────────────────┐
│  Refract                    ⚙  (glass) │  ← GlassToolbar, transparent until scroll
├────────────────────────────────────────┤
│  ╭──────────────────────────────────╮  │
│  │  Internal storage                │  │  ← StorageCard (glass, elevation 1)
│  │  84.2 GB of 128 GB used          │  │
│  │  ▓▓▓▓▓▓▓▒▒▒▒░░░░░░░░░░░          │  │  ← StorageMeter, stacked by category
│  │  43.8 GB free                    │  │
│  ╰──────────────────────────────────╯  │
│                                        │
│  [ + Folder ] [ 🔍 ] [ 🕐 ] [ 📊 ]     │  ← QuickAction row (glass chips)
│                                        │
│  Categories                            │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐           │
│  │Img │ │Vid │ │Aud │ │Doc │           │  ← 4×2 grid, opaque tiles
│  └────┘ └────┘ └────┘ └────┘           │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐           │
│  │Arch│ │APK │ │Dnld│ │Big │           │
│  └────┘ └────┘ └────┘ └────┘           │
│                                        │
│  Pinned                          ›     │  ← only when non-empty
│  ◉ Camera   ◉ Projects   ◉ Music      │
│                                        │
│  Recent                     See all ›  │
│  ▸ report.pdf        1.2 MB · today    │
│  ▸ IMG_4821.jpg      3.4 MB · today    │
│  ▸ backup.zip        112 MB · yesterday│
│  … (6 rows)                            │
│                                        │
│  Storage                               │
│  ▸ Internal storage       43.8 GB free │
│  ▸ SD card                12.1 GB free │
├────────────────────────────────────────┤
│    ⌂     ▤     🔍     ◷     ⋯          │  ← LiquidBottomBar (floating glass)
└────────────────────────────────────────┘
```

Order is deliberate: the answer to the most common problem (storage) is above the fold, and
the filesystem is deliberately *not* on this screen.

## 3. Components

`GlassToolbar` · `StorageCard` + `StorageMeter` · `QuickAction` ×4 · category tiles ·
`FileRow` (favourites, recents) · volume rows · `PermissionCard` (conditional) ·
`LiquidBottomBar`.

## 4. UI state

```kotlin
data class HomeUiState(
    val primaryStorage: StorageMeterUi? = null,
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val categories: List<CategoryTileUi> = FileCategory.defaults(),
    val favorites: List<FileNodeUi> = emptyList(),
    val recents: List<FileNodeUi> = emptyList(),
    val accessLevel: AccessLevel = AccessLevel.NONE,
    val isRefreshing: Boolean = false,
    val sectionsLoading: Set<HomeSection> = HomeSection.all,
    val error: FileError? = null,
)
```

Each section loads independently. A slow storage query never blocks recents from appearing.

## 5. Interactions

| Action | Result |
|---|---|
| Tap storage card | → Storage tab |
| Tap a category tile | → Category screen |
| Tap a quick action | New folder sheet / Search / Recents / Storage scan |
| Tap a recent file | → Preview |
| Long press a recent file | Selection mode within the recents list |
| Tap a favourite | → Folder or Preview |
| Long press a favourite | Context menu: Open, Unpin, File info |
| Tap a volume | → Folder at that volume's root |
| Pull down | Refresh all sections |
| Tap settings | → Settings |

## 6. Animations

* Sections fade + rise 8dp as they resolve, staggered 40 ms, `Motion.Gentle`.
* Storage meter segments animate from 0 width on first paint, `Motion.Standard`, once per
  cold start only — never on every return to the tab.
* Category tiles compress to 0.96 on press, `Motion.Snappy`.
* Tab entrance is a cross-fade + 6dp rise; no horizontal slide.

## 7. Edge cases

| Case | Behaviour |
|---|---|
| No access granted | `PermissionCard` replaces the storage card; categories show as locked with a single "Grant access" action; volumes still list with free space (available without permission) |
| `MEDIA_ONLY` access | Categories work; the Documents/Archives/APK tiles show "Limited" and explain why |
| Partial media grant (API 34+) | Images/Video tiles show a "selected only" badge with a Manage action |
| SD card present but ungranted | Volume row shows "Tap to allow access" |
| No recents (fresh device) | Section hidden entirely, not shown empty |
| No favourites | Section hidden entirely |
| Storage scan never run | Meter shows used/free only, no category breakdown, with "Analyse" |
| Volume removed while on screen | Row disappears with a fade; if it was the primary, show an error banner |

## 8. Accessibility

* Screen title is a `heading()`. Each section header is a `heading()`.
* The storage meter is one focusable node reading the full sentence; each legend entry is
  separately focusable and navigable.
* Category tiles: "Images, 12,430 files" — count included when known.
* Recent rows use the standard `FileRow` semantics with custom actions.
* All content is reachable by D-pad in order: toolbar → storage → quick actions → categories
  → favourites → recents → volumes → bottom bar.

## 9. Responsive

| Class | Layout |
|---|---|
| Compact | As drawn; categories 4×2 |
| Compact height (landscape phone) | Storage card collapses to a single line; categories 8×1 scrollable |
| Medium | Two columns: storage + quick actions + categories on the left, favourites + recents on the right |
| Expanded | Same two-column content in the list pane; the detail pane shows the storage breakdown by default |

## 10. Performance

* First frame paints skeletons; no section blocks it.
* Volume enumeration is cached and refreshed only on mount events.
* Recents query is limited to 6 rows with a projection — never fetch all and slice.
* Category counts come from the cached scan; if absent, tiles show no count rather than
  triggering a scan.
