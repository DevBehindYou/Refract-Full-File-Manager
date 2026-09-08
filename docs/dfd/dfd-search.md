# DFD — Search (Process 3.0 expanded)

```mermaid
flowchart TD
    USER([User])
    P11["1.0 Access management"]

    P31["3.1 Parse query<br/>+ debounce 250ms"]
    P32["3.2 Determine searchable scope"]
    P33["3.3 Query index (V1)"]
    P34["3.4 Query MediaStore"]
    P35["3.5 Walk filesystem / SAF"]
    P36["3.6 Merge + deduplicate"]
    P37["3.7 Apply filters"]
    P38["3.8 Rank by relevance"]
    P39["3.9 Throttled emit (10 Hz)"]

    D2[("D2 MediaStore")]
    D1[("D1 Device storage")]
    D3b[("D3.b Search index")]
    D3c[("D3.c Search history")]

    USER -->|"keystrokes"| P31
    P31 -->|SearchQuery| P32
    P11 -->|access level + grants| P32
    P32 -->|"scope + coverage warning"| USER
    P32 --> P33 & P34 & P35
    P33 <--> D3b
    P34 --> D2
    P35 --> D1
    P33 & P34 & P35 -->|"FileNode streams"| P36
    P36 -->|"unique by FileNodeId"| P37
    P37 --> P38
    P38 --> P39
    P39 -->|"results + count + isSearching"| USER
    USER -->|submit| D3c
    D3c -->|recent queries| USER
```

## Concurrency

```mermaid
sequenceDiagram
    participant Q as Query flow
    participant IX as Index
    participant MS as MediaStore
    participant W as Walker
    participant M as Merger
    participant UI

    Q->>Q: debounce 250ms, distinctUntilChanged
    Q->>M: flatMapLatest → cancels all previous sources
    par concurrent
        M->>IX: query
        IX-->>M: results in ~40ms
    and
        M->>MS: query
        MS-->>M: results in ~200ms
    and
        M->>W: walk (breadth-first, batches of 200)
        W-->>M: batches over seconds
    end
    M->>UI: emit ≤10 Hz, deduped, ranked
    Note over W: yield() per batch → instant cancellation on the next keystroke
```

## Coverage honesty

`3.2` produces a `CoverageWarning` whenever the searchable scope is narrower than what the
user asked for:

| Situation | Warning shown |
|---|---|
| API 29, no tree grants | "Search covers media and folders you've allowed. Add a folder." |
| API 30+, no All Files Access | Same, with an "Allow all files" action |
| Partial media grant (API 34+) | "Only selected photos are searchable. Manage selection." |
| Volume unmounted | "SD card isn't available." |

The scope chip always reflects reality. It never says "All storage" when it means "the two
folders you granted".

## Termination

* Walk stops at depth 32, at 5,000 results, on cancellation, or on completion.
* Reaching the result cap shows "Showing the first 5,000 — refine your search", never a
  silent truncation.
