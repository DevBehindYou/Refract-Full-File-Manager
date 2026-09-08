# Screen — Storage Overview

## 1. Purpose

Answer "what is filling my phone and what can I safely delete". This is the screen that
differentiates Refract from the stock file app, so it is a destination, not a Home section.

## 2. Layout

```text
┌────────────────────────────────────────┐
│  Storage                    Rescan ⟳   │
│  [ Internal ] [ SD card ]              │  ← volume segmented control
├────────────────────────────────────────┤
│  ╭──────────────────────────────────╮  │
│  │       84.2 GB used               │  │
│  │  ▓▓▓▓▓▓▓▒▒▒▒▓▓░░░░░░░░░░         │  │  ← single stacked bar
│  │  of 128 GB · 43.8 GB free        │  │
│  ╰──────────────────────────────────╯  │
│  Scanned 2 hours ago                   │
│                                        │
│  ● Images        32.1 GB   38%      ›  │
│  ● Video         21.4 GB   25%      ›  │
│  ● Audio          8.2 GB   10%      ›  │
│  ● Documents      3.1 GB    4%      ›  │
│  ● Archives       2.8 GB    3%      ›  │
│  ● Apps (APK)     1.2 GB    1%      ›  │
│  ● Other         15.4 GB   18%      ›  │
│                                        │
│  Largest folders                  ›    │
│  📁 DCIM/Camera            28.4 GB     │
│  📁 Android/media          11.2 GB     │
│  📁 Download                6.8 GB     │
│                                        │
│  Largest files                    ›    │
│  🎬 vacation.mp4            4.2 GB     │
│  📦 backup-2026.zip         2.1 GB     │
│                                        │
│  Clean up                              │
│  ▸ Duplicate files      (V1)      ›    │
│  ▸ Large files (>100 MB)          ›    │
│  ▸ Empty folders        (V1)      ›    │
│  ▸ Trash                 1.2 GB   ›    │
└────────────────────────────────────────┘
```

**One stacked bar, not a pie.** Length comparison is more accurate than angle comparison,
and the bar survives a 320dp screen.

## 3. UI state

```kotlin
data class StorageUiState(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val selectedVolumeId: String? = null,
    val totals: VolumeTotals? = null,
    val breakdown: List<CategorySlice> = emptyList(),
    val largestFolders: List<FileNodeUi> = emptyList(),
    val largestFiles: List<FileNodeUi> = emptyList(),
    val trashSize: Long = 0,
    val scanState: ScanState = ScanState.Idle,   // Idle | Scanning(progress, current) | Cached(at) | Failed
    val scannedAt: Long? = null,
)
```

## 4. Interactions

| Action | Result |
|---|---|
| Volume segment | Switch volume; cached result shows instantly |
| Rescan | Start a scan; the bar animates from cached values to new ones |
| Tap a category row | → Category screen filtered to this volume |
| Tap a largest folder | → that folder |
| Tap a largest file | → Preview |
| Tap "Large files" | Filtered list, multi-select enabled, sorted descending |
| Tap Trash | Trash screen: restore or empty |
| Long press any listed item | Selection mode for bulk action |

## 5. Animations

* Bar segments animate width from the cached value to the new value with `Motion.Gentle` —
  never from zero on a rescan, or the whole bar flickers.
* During a scan, the bar shows a subtle indeterminate shimmer on the "unknown" remainder only.
* Legend rows fade in staggered 30 ms as their totals resolve.
* Scan progress is per top-level directory, so the indicator moves visibly.

## 6. Edge cases

| Case | Behaviour |
|---|---|
| Never scanned | Show used/free from `StatFs` immediately; breakdown shows "Analyse storage" |
| Scan cancelled | Keep partial results, label them "Partial scan" |
| Scan in progress, user leaves | Scan continues; returning shows live progress |
| Restricted access | Breakdown covers only reachable storage, with a banner saying so and a Grant action |
| Volume removed mid-scan | Scan aborts cleanly, cached result retained and marked stale |
| "Other" is the largest category | Expanding it lists the largest contributing folders — never leave 15 GB unexplained |
| Free space reported differently by the system | Show `StatFs` numbers as authoritative for used/free; the breakdown sums to used, with a rounding note |

## 7. Accessibility

* The bar is one focusable node reading the full sentence: "84.2 gigabytes of 128 used.
  Images 32.1, Video 21.4…".
* Each legend row is separately focusable and navigable, with colour **and** glyph.
* Scan progress is a live region announced at 25% intervals.
* Colours are the colour-blind-safe category accents; the bar segments are additionally
  separated by 1dp gaps so boundaries are visible without colour discrimination.

## 8. Responsive

| Class | Layout |
|---|---|
| Compact | As drawn |
| Medium | Bar and legend side by side; largest folders/files in two columns |
| Expanded | Breakdown in the list pane; selecting a category fills the detail pane with that category's file list |

## 9. Safety

Cleanup never deletes anything without per-item confirmation. The duplicate finder never
pre-selects every copy in a group. Bulk delete from this screen uses the trash by default and
shows total size in the confirmation.
