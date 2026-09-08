# DFD Level 1 — Major Processes

```mermaid
flowchart TD
    USER([User])
    OTHER([Other apps])
    OS([Android platform])

    P1["1.0 Access &<br/>Permission Management"]
    P2["2.0 Storage Discovery<br/>& Browsing"]
    P3["3.0 Search"]
    P4["4.0 File Operations"]
    P5["5.0 Storage Analysis"]
    P6["6.0 Preview & Sharing"]
    P7["7.0 Archive Handling"]
    P8["8.0 Preferences &<br/>Presentation"]

    D1[("D1 Device storage")]
    D2[("D2 MediaStore")]
    D3[("D3 Room")]
    D4[("D4 DataStore")]
    D5[("D5 Thumbnails")]

    USER -->|grant decisions| P1
    P1 -->|access level| P2 & P3 & P4 & P5
    P1 <-->|"requests, results"| OS
    P1 -->|persisted grants| D3

    USER -->|navigate| P2
    P2 <--> D1
    P2 --> D2
    P2 -->|node lists| USER
    P2 -->|size cache| D3
    P2 --> D5

    USER -->|query + filters| P3
    P3 --> D1
    P3 --> D2
    P3 <-->|index, history| D3
    P3 -->|streamed results| USER

    USER -->|"copy/move/delete/rename"| P4
    P4 <--> D1
    P4 <-->|"queue, progress, trash, undo"| D3
    P4 -->|progress + notifications| OS
    P4 -->|status| USER
    P4 -->|invalidate| P2

    USER -->|scan| P5
    P5 --> D1
    P5 --> D2
    P5 <-->|cached breakdown| D3
    P5 -->|breakdown, largest items| USER

    USER -->|open / share| P6
    P6 --> D1
    P6 -->|content:// URI| OTHER
    OTHER -->|incoming URI| P6
    P6 -->|rendered content| USER
    P6 -->|recents| D3

    USER -->|"extract / compress"| P7
    P7 <--> D1
    P7 -->|as an operation| P4

    USER -->|settings| P8
    P8 <--> D4
    P8 -->|"theme, glass tier, view mode"| P2 & P3 & P5 & P6
    OS -->|"thermal, battery, a11y"| P8
```

## Process responsibilities

| # | Process | Inputs | Outputs | Key stores |
|---|---|---|---|---|
| 1.0 | Access & permissions | User decisions, platform grants | `AccessLevel`, tree grants | D3 |
| 2.0 | Storage discovery & browsing | Path, sort, filters, access level | Node lists, breadcrumb, volumes | D1, D2, D3, D5 |
| 3.0 | Search | Query, filters, scope | Ranked, deduplicated results | D1, D2, D3 |
| 4.0 | File operations | Sources, destination, options | Progress, summary, undo token | D1, D3 |
| 5.0 | Storage analysis | Volume, scan trigger | Category breakdown, top-N | D1, D2, D3 |
| 6.0 | Preview & sharing | Node id | Rendered content, share intents | D1, D3 |
| 7.0 | Archives | Archive node, selection, destination | Extracted tree / new archive | D1 |
| 8.0 | Preferences & presentation | Settings, platform state | Theme, glass tier, defaults | D4 |

## Cross-cutting flows worth noticing

* **1.0 gates 2.0–5.0.** No process reads storage without consulting the access level first.
* **7.0 delegates to 4.0.** Archive work is executed by the operations engine so it inherits
  progress, cancellation, collision handling and the foreground service — rather than
  reimplementing them.
* **4.0 invalidates 2.0.** Completing an operation refreshes the affected directory listing;
  this is the reliable refresh path, since file watching is not dependable.
* **8.0 feeds presentation only.** No preference changes what is *possible*, only what is
  *shown* — except `showHidden` and `indexEnabled`, which are filters, not capabilities.
