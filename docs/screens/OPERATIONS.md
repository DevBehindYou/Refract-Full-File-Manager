# Screen — Operation Progress

Companion to `../architecture/FILE_OPERATIONS.md`, which owns the engine. This file owns only
what the user sees.

## 1. Purpose

Make long file operations visible, cancellable, and trustworthy — while letting the user carry
on browsing. The user must never have to sit and watch a copy.

## 2. Three surfaces, one source of truth

All three render the same `OperationSnapshot` stream from the service. There is no second
progress state anywhere in the app.

| Surface | When | Content |
|---|---|---|
| **Notification** | Always, while any operation runs | Title, current file, progress, Pause, Cancel |
| **Inline banner** | Any screen, while operations run | Compact glass strip above the bottom bar: "Copying 42 of 310 · 68%" + Cancel; tap → sheet |
| **Operations sheet** | Tapping the banner or the Recents quick action | Full list of active, queued, and recently finished operations |

## 3. Layout — operations sheet

```text
┌────────────────────────────────────────┐
│  Transfers                        ✕    │
├────────────────────────────────────────┤
│  Copying to Documents                  │
│  IMG_4823.jpg                          │
│  ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░  68%              │
│  42 of 310 files · 1.2 GB of 4.4 GB    │
│  18 MB/s · about 3 min left            │
│        [ Pause ]   [ Cancel ]          │
├────────────────────────────────────────┤
│  Queued                                │
│  ▸ Move 12 files to SD card            │
├────────────────────────────────────────┤
│  Recent                                │
│  ✓ Copied 8 files to Download   Undo   │
│  ⚠ Deleted 3 files, 1 skipped   Details│
└────────────────────────────────────────┘
```

```kotlin
data class OperationsUiState(
    val active: List<OperationCardUi> = emptyList(),
    val queued: List<OperationCardUi> = emptyList(),
    val recent: List<OperationResultUi> = emptyList(),
)

data class OperationCardUi(
    val id: OperationId,
    val kind: OperationKind,          // Copy, Move, Delete, Extract, Compress
    val destinationLabel: String?,
    val currentFileName: String?,
    val filesDone: Int, val filesTotal: Int,
    val bytesDone: Long, val bytesTotal: Long,
    val throughputBytesPerSec: Long?,
    val etaSeconds: Long?,
    val status: OperationStatus,      // Running, Paused, WaitingForInput, Cancelling
)
```

## 4. Progress rules

* **Progress is byte-based, not file-based.** 42 of 310 files can be 4% or 96% of the bytes.
  The bar tracks bytes; the count is secondary text.
* Emissions are throttled to **4 Hz**. A per-buffer progress callback at 60 Hz spends more time
  recomposing than copying.
* **Throughput** is a 5-second rolling average, not instantaneous, or the number is unreadable.
* **ETA** appears only after 3 seconds and only when throughput has been stable enough to mean
  something. An ETA that swings from 12 s to 40 min is worse than no ETA. It is phrased
  approximately ("about 3 min left"), never as a countdown.
* When total bytes are unknown (SAF sources sometimes cannot report size cheaply), the bar is
  indeterminate and the label shows files and bytes copied so far. We never fake a percentage.

## 5. Interactions

| Action | Result |
|---|---|
| Pause | Finishes the current file's in-flight buffer, then stops. Partial target for the *next* file is never left behind |
| Resume | Continues from the next unfinished file; a partially copied file restarts from zero (byte-range resume is out of scope) |
| Cancel | Confirm if more than 10 files or 100 MB have completed. Deletes the in-progress target only; completed files stay, and the summary says exactly what was done |
| Tap a conflict prompt | Conflict sheet: Replace / Keep both / Skip, with an "Apply to all remaining" checkbox |
| Undo (recent) | Available for copy (delete the copies), move (move back), trash (restore). Never for permanent delete |
| Details (partial failure) | Per-file list of what failed and why |
| Swipe a recent entry | Dismiss it |

## 6. Conflict handling

Operations do not silently choose. On the first conflict the operation enters
`WaitingForInput`, the notification becomes "Action needed", and the sheet shows both files
side by side: name, size, modified date, and a thumbnail for media. The user picks
Replace / Keep both / Skip, optionally for all remaining conflicts. If the app is not
foreground, the operation waits — it does not time out and it does not guess.

## 7. Animations

* Progress bar interpolates between the 4 Hz updates with a linear tween so it moves smoothly
  without lying about intermediate values.
* Percentage text cross-fades; the byte counter uses a monospace-tabular figure so digits do
  not jitter.
* Completion: the bar fills to 100%, holds 400 ms, the card collapses into the Recent section.
* Failure: the card tints to the error container colour with a 200 ms cross-fade — no shake,
  no flash.
* The inline banner rises with `Motion.Standard` and pushes the bottom bar's content up rather
  than covering it.

## 8. Edge cases

| Case | Behaviour |
|---|---|
| App killed mid-operation | Foreground service survives; on relaunch the banner shows the operation still running |
| Service killed by the system | Queue row is marked `INTERRUPTED`; on next launch the user is offered Resume or Discard, and any partial target is cleaned up first |
| Destination fills mid-copy | Operation pauses with "Not enough space — 1.4 GB more needed"; freeing space and tapping Retry resumes |
| Source volume removed | Operation fails fast, partial target deleted, message names the volume |
| Battery saver / Doze | Foreground service continues; we do not schedule this as deferrable work |
| Two operations targeting the same folder | Both run; the queue serialises writes per destination volume |
| Zero-byte file in the set | Counted, copied, and verified like any other |
| User navigates into the destination while copying | New files appear as they complete, each with a brief highlight |
| Notification permission denied (API 33+) | Operations still run; the inline banner becomes the only surface, and Settings explains the trade-off |

## 9. Accessibility

* Progress is a live region announced at **10% intervals only**, plus on completion, pause,
  and failure. Announcing every update makes TalkBack unusable.
* Announcements are phrased usefully: "Copying, 68 percent, about 3 minutes left".
* Pause and Cancel are ≥ 48dp and are the first focusable elements in the card.
* Conflict prompts move focus to the sheet and announce the choice required.
* The completion summary is announced once, not per file.

## 10. Responsive

| Class | Layout |
|---|---|
| Compact | Bottom sheet, banner above the bottom bar |
| Medium | Same sheet, wider; the banner sits above the rail's content area |
| Expanded | Operations render as a persistent panel in the detail pane when opened, so browsing and monitoring happen together |
