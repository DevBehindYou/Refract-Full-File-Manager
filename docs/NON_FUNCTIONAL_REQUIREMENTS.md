# Non-Functional Requirements

Reference device for all budgets: **mid-tier, Snapdragon 6-series class, 6 GB RAM,
Android 14**. Flagship targets are 1.6× tighter; the low-end floor is a Go-class device
on Android 8.1 where budgets are 2× looser but must still avoid ANR.

---

## NFR-1 Performance budgets

| Operation | Target (p50) | Ceiling (p90) | Fail condition |
|---|---|---|---|
| Cold start → first frame | 500 ms | 800 ms | > 1200 ms |
| Cold start → interactive Home | 700 ms | 1000 ms | > 1500 ms |
| Warm start | 200 ms | 350 ms | > 600 ms |
| Open folder (< 500 items) | 80 ms | 150 ms | > 400 ms |
| Open folder (10,000 items), first paint | 150 ms | 300 ms | > 800 ms |
| Search first result (indexed) | 120 ms | 300 ms | > 700 ms |
| Search first result (filesystem walk) | 400 ms | 900 ms | > 2500 ms |
| Thumbnail decode + display | 60 ms | 120 ms | visible pop-in > 300 ms |
| Storage scan (128 GB, 200k files) | 25 s | 45 s | > 90 s |
| Frame time, scrolling | 8 ms | 16 ms | any frame > 32 ms |
| Glass render cost per frame | 2 ms | 4 ms | > 6 ms → tier demotion |
| Copy throughput, internal → internal | ≥ 60% of `dd` baseline | | < 35% |

## NFR-2 Memory

* Peak heap during any operation: **< 180 MB** on the reference device.
* Thumbnail cache: memory 1/8 of `maxMemory`, disk 250 MB with LRU eviction.
* Directory listings are paged into the UI; the full `FileNode` list for a directory is
  capped at 50,000 entries in memory, beyond which the repository pages from disk.
* **No file content is ever fully buffered.** All I/O uses 64–256 KB streaming buffers.
* Archive extraction streams entry by entry; never `toByteArray()` an entry.
* `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` clears the thumbnail memory cache and demotes the
  glass tier for the session.

## NFR-3 Reliability

| Requirement | Target |
|---|---|
| Crash-free sessions | ≥ 99.7% |
| ANR rate | ≤ 0.1% |
| Data-loss incidents | 0 (P0 stop-ship) |
| Operation completion under process death | Resumed or reported, never silently lost |
| Orientation change / config change | Zero state loss, zero operation interruption |
| Recovery from revoked permission mid-session | Graceful, with a route back to re-grant |
| Behaviour on storage removal mid-operation | Operation cancelled, partial target cleaned, user informed |

## NFR-4 Compatibility

* API 27 → 37 inclusive. Every screen renders and every MVP flow completes on API 27.
* Screen sizes 320dp → 1600dp width; portrait, landscape, folded, unfolded, multi-window,
  and free-form desktop windows.
* No orientation lock. No minimum-size lock (required by API 36 large-screen rules).
* RTL layouts fully supported.
* 16 KB page size compatible (relevant if any native library is ever linked).

## NFR-5 Security

* No exported components except the launcher activity and the `FileProvider`.
* All shared URIs are `content://` via `FileProvider` with least-privilege flags and no
  `file://` URIs ever leaving the app.
* All archive extraction is path-validated against the destination root.
* All incoming `Intent` extras are validated; no path is trusted from an external caller.
* No dynamic code loading, no reflection into platform internals.
* Full detail in `architecture/SECURITY.md`.

## NFR-6 Privacy

* Zero network calls at MVP outside opt-in crash reporting.
* No filename, path, or file content is ever transmitted or written to a crash report.
* No analytics SDK. No advertising ID access. No `READ_PHONE_STATE`.
* See `PRIVACY.md`.

## NFR-7 Accessibility

* WCAG 2.2 AA for contrast on all text and meaningful icons.
* All interactive targets ≥ 48×48dp with ≥ 8dp separation.
* TalkBack: complete labels, correct traversal order, live regions for progress.
* Layout survives font scale 200% + display size "largest" with no clipping or overlap.
* Reduce motion and reduce transparency fully honoured.
* No information conveyed by colour alone.

## NFR-8 Maintainability

* Kotlin, `explicitApi()` off but public API documented in shared modules.
* ktlint + detekt in CI, zero warnings on `main`.
* Domain layer has **zero** `android.*` imports — enforced by a lint rule.
* Test coverage: ≥ 80% on domain and data layers, ≥ 60% overall.
* Every API-level branch is behind a named constant and covered by a test on both sides.

## NFR-9 App size and build

* Release APK ≤ 12 MB, AAB delivered.
* R8 full mode, resource shrinking on.
* Baseline profile generated for start-up and browse scroll.
* Build reproducible; no snapshot dependencies.
