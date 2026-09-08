# DFD — Storage Analysis (Process 5.0 expanded)

```mermaid
flowchart TD
    USER([User])

    P51["5.1 Read volume totals"]
    P52["5.2 Check cache freshness"]
    P53["5.3 Single-pass walk"]
    P54["5.4 Categorise by MIME/extension"]
    P55["5.5 Aggregate category totals"]
    P56["5.6 Maintain top-N heaps"]
    P57["5.7 Persist scan result"]
    P58["5.8 Find duplicates (V1)"]
    P59["5.9 Build cleanup suggestions (V1)"]

    D1[("D1 Device storage")]
    D2[("D2 MediaStore")]
    D3g[("D3.g Scan cache")]

    USER -->|open Storage| P51
    P51 -->|"used / free / total"| USER
    P51 --> P52
    P52 <--> D3g
    P52 -->|"fresh (< 24h)"| USER
    P52 -->|stale or forced| P53
    P53 --> D1
    P53 --> D2
    P53 --> P54
    P54 --> P55
    P54 --> P56
    P55 -->|"progress + partial breakdown"| USER
    P56 -->|"top 20 folders, top 50 files"| USER
    P55 & P56 --> P57 --> D3g
    USER -->|find duplicates| P58
    P58 --> D1
    P58 -->|"groups, none pre-selected"| USER
    P55 & P56 & P58 --> P59
    P59 -->|"suggestions, per-item confirmation"| USER
```

## Why a single pass

Three separate walks (categories, largest files, largest folders) would triple the I/O on a
200,000-file volume. One walk feeds all three aggregators:

```kotlin
walk(root) { node ->
    categoryTotals.merge(node.category, node.size, Long::plus)
    largestFiles.offer(node)                       // bounded min-heap, size 50
    folderAccumulator.add(node.parentId, node.size)
}
// top folders derived from folderAccumulator at the end, also via a bounded heap
```

Bounded heaps mean memory is O(N) in the *result* size, not the file count.

## Duplicate detection (V1) — three stages

```mermaid
flowchart TD
    A["All files (200k)"] --> B["Stage 1: group by exact size<br/>zero file reads"]
    B --> C{"Group size > 1?"}
    C -->|no| D[Discard ~95%]
    C -->|yes| E["Stage 2: hash first 64KB + last 64KB<br/>2 reads per file"]
    E --> F{Still grouped?}
    F -->|no| D
    F -->|yes| G["Stage 3: full SHA-256<br/>full read, tiny remainder"]
    G --> H[Confirmed duplicate groups]
    H --> I["UI: pre-select all but one per group,<br/>user confirms every deletion"]
```

**Safety rule:** never auto-select every copy in a group, and never delete without explicit
confirmation. The failure mode here is destroying someone's only copy of a photo.

## Progress and cancellation

* Progress is reported per top-level directory so the bar advances meaningfully rather than
  sitting at 3% for a minute.
* Cancellable at every directory boundary; a cancelled scan keeps its partial results and
  labels them as partial.
* Runs at background thread priority; never blocks browsing.
* Results are shown from cache instantly with a "Scanned 2 hours ago · Rescan" affordance.

## Visualisation constraint

The output is rendered as **one horizontal stacked bar plus a legend** — not a pie, donut, or
treemap. Length comparison beats angle comparison for accuracy, and the stacked bar scales
down to a 320dp screen without becoming unreadable. See `../COMPONENT_LIBRARY.md`
(`StorageMeter`).
