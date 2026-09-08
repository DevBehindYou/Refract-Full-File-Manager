# Screen — Search and Search Results

## 1. Purpose

Find a file by name in under three seconds, across as much storage as the user has actually
granted — and be honest about the difference.

## 2. Layout

```text
┌────────────────────────────────────────┐
│  ‹  [🔍 report                    ✕ ]  │  ← GlassSearchBar, focused on entry
│  [ All storage ▾ ] [ Type ] [ Size ]   │  ← scope + filter chips
│  ⚠ Searching media and 2 allowed folders  ← coverage warning (conditional)
├────────────────────────────────────────┤
│  142 results · still searching…   ⏹    │
│                                        │
│  📄 report.pdf              1.2 MB     │
│     Internal › Documents          ›    │
│  📄 report_final.pdf        1.4 MB     │
│     Internal › Download           ›    │
│  📁 Reports                 12 items   │
│     Internal › Documents          ›    │
└────────────────────────────────────────┘
```

Empty query state shows recent searches and suggested filters instead of results.

## 3. UI state

```kotlin
data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.AllStorage,
    val filters: SearchFilters = SearchFilters(),
    val results: List<SearchResultUi> = emptyList(),
    val resultCount: Int = 0,
    val isSearching: Boolean = false,
    val hitResultCap: Boolean = false,
    val recentQueries: List<String> = emptyList(),
    val coverageWarning: CoverageWarning? = null,
    val selection: Set<FileNodeId> = emptySet(),
)

data class SearchFilters(
    val categories: Set<FileCategory> = emptySet(),
    val extensions: Set<String> = emptySet(),
    val sizeRange: SizeBucket? = null,
    val dateRange: DateBucket? = null,
)
```

## 4. Results behaviour

* Results stream in from three concurrent sources, deduplicated, emitted at ≤ 10 Hz.
* Sort is stable: exact match → prefix → word boundary → substring, then recency. New results
  **append below the viewport** unless the list is scrolled to the top, so nothing jumps under
  the user's finger.
* Each result shows its containing folder as a tappable chip that navigates there with the
  item highlighted.
* A live counter with a stop button; stopping keeps the results found so far.
* At 5,000 results: "Showing the first 5,000 — add a filter to narrow this."

## 5. Interactions

| Action | Result |
|---|---|
| Type | Debounced 250 ms, previous search cancelled |
| Tap scope chip | Sheet: This folder / A volume / All storage |
| Tap a filter chip | Sheet for that filter; active filters show a count badge |
| Tap result | → Preview (file) or → Folder (folder) |
| Tap folder chip on a result | → that folder, item highlighted |
| Long press result | Selection mode across results |
| Submit (IME Search) | Save to recent searches |
| Tap recent query | Re-run it |
| Clear (✕) | Clear query, return to the recent-searches state |
| Back | Clear focus; if already unfocused, leave Search |

## 6. Animations

* Search bar expands to full width on focus, `Motion.Standard`; back arrow rotates in.
* Results fade + rise 6dp as they arrive, staggered 20 ms over at most 8 items.
* Filter chips animate their badge count with `Motion.Snappy`.
* The counter number cross-fades rather than sliding — a rolling odometer at 10 Hz is noise.

## 7. Edge cases

| Case | Behaviour |
|---|---|
| Empty query | Recent searches + suggested filters, no results list |
| No results, search finished | Empty state: "Nothing matched *query*" + Clear filters |
| No results, still searching | Skeleton rows + "Searching…", never the empty state |
| Restricted scope | Coverage warning banner naming exactly what is searched, with an action to grant more |
| Partial media grant (API 34+) | Warning + Manage selection |
| Query with only a wildcard | Treated as invalid; hint explains the syntax |
| Volume unmounted mid-search | That source stops; results already found stay; a note appears |
| 40,000 matches | Capped at 5,000 with a clear message |
| Rotation mid-search | Query, filters, and results preserved; the search continues |

## 8. Accessibility

* The search field is `Role.SearchField` with `imeAction = Search`.
* The result count is a **live region**, announced when it changes by more than 10 or when the
  search completes — not on every update.
* Each result reads: name, type, size, then "in Documents".
* The coverage warning is announced when it appears, once.
* Filters announce their state ("Type filter, 2 selected").

## 9. Responsive

| Class | Layout |
|---|---|
| Compact | Full-screen search, bottom bar hidden while focused |
| Medium | Search bar in the top region, results in a wider list, filters expanded inline |
| Expanded | Results in the list pane, preview of the highlighted result in the detail pane; arrow keys move the highlight and update the preview |

## 10. Performance

Targets and mechanisms are in `../architecture/SEARCH_ARCHITECTURE.md` §7. The three that
matter on this screen: 250 ms debounce, `flatMapLatest` cancellation, and the 10 Hz emit
throttle. Without the throttle, a broad query at 40,000 matches makes the UI unusable.
