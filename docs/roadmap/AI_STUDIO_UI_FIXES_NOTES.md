# UI fixes and multi-select — notes

Applied directly to the project as it came back from Google AI Studio (12 UI files added
by its Gemini agent on top of the original Phase 1–3 codebase, everything else
untouched). Two files were modified: `BrowseScreen.kt` (substantial rewrite) and
`FileListItem.kt` (selection-mode support added).

Same standing caveat as every other notes file in this project: none of this has been
compiled. If anything, treat this pass as *higher* risk than the original Phase 1–3 work,
not lower — this is the first code in the project written by reading and modifying
someone else's (Gemini's) generated code rather than building from the spec directly, and
several real bugs were caught purely through careful re-reading, not execution. That's
weaker evidence than a passing test. Three are worth naming specifically, because they're
exactly the kind of mistake that's easy to reintroduce:

- A fabricated `Modifier.semantics_testTag(...)` call (not a real API) in an early draft —
  caught on review, fixed to `Modifier.testTag(...)`.
- `RenameDialog` was called with a named argument `currentName`, but the component's real
  parameter is `initialName` — caught only by grepping the actual component signature, not
  by re-reading my own code in isolation.
- A stray, meaningless `animateDpAsState(targetValue = 0.dp, label = "unused")` line left
  over from an abandoned idea — dead code that would have compiled fine and done nothing
  useful.

None of that inspires confidence that everything else is right — it's the reason the
verification section below lists exactly what was and wasn't cross-checked.

## 1. The performance bug — root cause and fix

**Reported:** delay opening folders with thousands of files.

**Root cause:** `BrowseScreen`'s original loading logic called
`items = accumulated.toList()` on every ~200-item chunk from the backend's (correctly
streaming) `listChildren()` Flow. That reassignment fed into
`val filteredItems = remember(items, searchQuery, sortOption) { ... sortedWith(...) }` —
which re-ran its *entire* sort over the whole accumulated-so-far list on every single
chunk, synchronously, during composition (the main thread). For a 10,000-item folder
that's roughly 50 increasingly expensive re-sorts. Worse: `isLoading` didn't flip to
`false` until the whole flow completed, so none of that work was even visible — the user
just saw a frozen spinner for however long all 50 re-sorts took.

**Fix**, in `collectListingThrottled` (new) and the `LaunchedEffect` driving
`filteredItems`:
- The raw list (`rawItems`) only pushes a new Compose state value at most once every
  120ms during streaming (always on the first and last chunk regardless), instead of on
  every chunk.
- The actual filter+sort pass moved out of a synchronous `remember` block into a
  `LaunchedEffect(rawItems, searchQuery, sortOption)` that computes on
  `Dispatchers.Default` — even the throttled updates never block the main thread.
- `isLoading` now flips to `false` after the *first* chunk, so the list appears
  immediately and grows, rather than staying hidden behind a spinner.

Also removed in the process: the original file had a fallback path that manually
reconstructed `FileNode` objects from `currentDir.listFiles()` when the backend's flow
failed with nothing yet accumulated. That duplicated (and could diverge from)
`FileSystemBackend`'s own node-construction logic. Removed rather than fixed — errors now
surface as a plain message instead of a second, parallel, possibly-inconsistent code path.

## 2. Multi-select, copy, move, delete

Selection mode is derived state (`selectedIds.isNotEmpty()`), not a separate boolean —
long-press on a row selects it and enters the mode; tap toggles while active, per the
requested spec. The per-item long-press menu (Details/Rename/Delete) was moved to an
explicit trailing "more" icon button instead, since long-press-only actions are exactly
the kind of low-discoverability, touch-only pattern `docs/ACCESSIBILITY.md` already
argues against — this is a net improvement on that front, not just a side effect of
adding selection mode.

**Copy/move use a clipboard pattern** (select → Copy or Cut → navigate anywhere → Paste),
not a folder-picker dialog — this is how MiXplorer and most file managers actually do it,
and it avoids needing to build a separate picker screen. A persistent "N item(s) ready to
copy/move — Paste here" bar appears once something's on the clipboard.

**Collision handling:** before pasting, every selected item is checked against the
current directory; if any collide, one dialog offers Overwrite / Keep Both / Skip,
applied to *all* conflicts in that paste — not resolved per-item. That's a real
simplification against the originally-requested "per-item ... Apply to All" behavior; a
true per-item flow (with its own "apply to all remaining" escape hatch) wasn't built this
pass.

**Recursive copy** (`copyNodeRecursively`) walks folders using the backend's own
`listChildren`/`openInput`/`openOutput`/`createDirectory`, not raw file I/O bypassing it.
One deliberate simplification: a folder-name collision is always treated as "keep both"
(a uniquified folder name), never a recursive merge into the existing folder — true
merge-on-overwrite needs per-level conflict resolution this pass doesn't implement.

**Move** (`moveNode`) uses `StorageBackend.moveWithin` directly when there's no name
collision. On a collision, it falls back to copy-then-delete-source for that one item,
since `moveWithin` can't express "rename on conflict" itself — the same reasoning
`FileSystemBackend.moveWithin`'s own KDoc already documents for why the backend doesn't
attempt this fallback internally.

**Not built:** background/queued operations (everything above runs in one coroutine tied
to the screen's lifecycle — cancel-and-resume, multiple simultaneous queued operations,
and survival past navigating away don't exist), keyboard shortcuts (Ctrl+C/X/V, Ctrl+A),
range-select, and undo after delete/paste. All of these were in the original request; none
were in scope for what could be done carefully in this pass.

## 3. Pull-to-refresh

Added via `PullToRefreshBox`/`rememberPullToRefreshState` (`androidx.compose.material3.
pulltorefresh`). **Moderate confidence, not high** — this is a newer Material3 API than
almost anything else already proven to compile in this project, and nothing in the
codebase used it before this change to cross-reference against. If anything in this
delivery fails to resolve, this is a reasonable first place to check.

## 4. What was already there, confirmed working by reading the code (not just assumed)

Breadcrumb navigation, distinct file-type icons (image/video/audio/document/archive/APK/
folder), the file details/properties dialog (size, MIME type, item count, modified date,
permissions), single-item delete confirmation, and a working sort menu (now with a
checkmark against the active option, added this pass) were all already implemented by
AI Studio's agent and are solid. No changes were needed beyond the checkmark indicator.

## 5. A real architectural finding, not fixed this pass — needs a decision

`MainActivity.kt`'s `@AndroidEntryPoint` annotation was removed at some point (by
whichever process generated this UI layer), and `BrowseScreen` constructs
`FileSystemBackend(context)` directly rather than going through Hilt or
`StorageBackendSelector`. Two concrete consequences:

- The Hilt DI graph built in Phase 3 (`BackendModule`, the `BackendType`-keyed map) is
  currently disconnected from the UI entirely.
- SAF and MediaStore browsing are structurally unreachable from this screen — it can only
  ever see `file:`-prefixed nodes, regardless of what's granted.

Separately: this screen's raw `java.io.File` usage throughout is exactly what the
`NoPlatformFileInUi` Lint rule (Phase 1) was built to catch — but it doesn't fire, because
the rule checks for `feature.*`/`core.ui` packages specifically, and this code lives under
`ui.screens`/`ui.components`, which the original architecture docs never used as a
package name. This is a real coverage gap in the rule, found by this code actually
existing, not anticipated when the rule was written.

None of this was touched in this pass — deliberately, since fixing it means either
migrating this whole screen to be `FileNodeId`-native (a much bigger, riskier change than
anything else here) or updating the Lint rule's package match (a small, low-risk fix on
its own, but one that doesn't address the underlying DI/architecture bypass). Worth a
deliberate decision before more UI gets built on top of the current pattern.

## 6. What's still genuinely missing against the original request list

Animation-spec compliance (the spring catalogue, `PredictiveBackHandler`,
reduce-motion handling), accessibility-spec compliance beyond what `FileListItem` already
had incidentally (no `CustomAccessibilityAction`s wired up, no verified `mergeDescendants`
grouping), dual-pane tablet layout, per-folder persisted view/sort settings, a bookmarks
drawer, and batch rename. None of these were touched this pass.
