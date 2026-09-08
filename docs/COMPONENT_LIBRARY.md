# Component Library

All components live in `core.designsystem.component` (glass primitives) or `core.ui.component`
(file-domain components). Every one is stateless, previewable, and takes state + lambdas.

Each entry documents: **API**, **states**, **gestures**, **motion**, **accessibility**,
**tier fallback**.

---

## Part 1 — Glass primitives

### `GlassSurface`
The foundation. Everything else composes it.

```kotlin
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Control,
    shape: Shape = RefractShapes.card,
    hazeState: HazeState = LocalHazeState.current,
    content: @Composable BoxScope.() -> Unit,
)
```
* **States:** rest, pressed (distortion ×1.35), disabled (opacity ×0.6, no highlight).
* **Motion:** entrance condense (200 ms); press bloom.
* **A11y:** contributes nothing to semantics. Adds an internal opaque tint layer beneath
  `content` sufficient for 4.5:1 text contrast.
* **Tier fallback:** A = blur+shader; B = blur+rim; C = scrim+gradient+border.

### `GlassCard`
`GlassSurface` with `shape.card`, `space.lg` padding, and elevation 1.
Used for the storage card and permission cards. **Not** used inside lists.

### `GlassButton` / `GlassIconButton`
* **States:** enabled, pressed, disabled, loading (glyph → 16dp indeterminate ring).
* Minimum 48dp target regardless of visual size.
* **A11y:** `Role.Button`, `contentDescription` mandatory on icon variant, `onClickLabel`
  for non-obvious actions.
* Ripple is replaced by the refraction bloom on Tier A/B; Tier C keeps the Material ripple.

### `LiquidBottomBar` + `LiquidBottomBarItem`
The signature component. See `ANIMATION_SYSTEM.md` §4 for motion.

```kotlin
@Composable
fun LiquidBottomBar(
    items: List<NavItem>,
    selectedRoute: String,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
)
```
* Floating pill, 64dp tall, inset `space.lg` horizontally and 12dp above the
  `navigationBars` inset (mandatory on API 35+ edge-to-edge).
* Animated indicator pill behind the active item, with velocity-driven stretch (Tier A/B).
* **States per item:** unselected, selected, pressed, with badge (operation count on More).
* **Gestures:** tap to select; tap on the already-selected item scrolls that tab to top;
  long press on Browse opens the volume switcher.
* **A11y:** `Role.Tab`, `selected = true/false`, `stateDescription` "selected",
  labels always present (never icon-only), `traversalIndex` after content.
* **Hides** when: selection mode active (replaced by the selection bar), a sheet is expanded,
  or the IME is visible.
* **Tier fallback:** C renders as a solid pill with a `primaryContainer` indicator. Layout
  identical.

### `GlassToolbar`
Top app bar that becomes glass only once content scrolls beneath it (`scrollBehavior`
provides the fraction, which drives `opacity` and `blurRadius` from 0 to target).
* **A11y:** title is a heading (`semantics { heading() }`); nav icon has a description.

### `GlassSearchBar`
* **States:** collapsed (on Home/Browse), focused (expanded, full width), with-query,
  loading (trailing 16dp ring), with-filters (leading chip count badge).
* **A11y:** `Role.SearchField` semantics, `imeAction = Search`, results announced via a
  live region ("42 results").

### `GlassSheet`
Bottom sheet on `GlassSurface`, `shape.sheet`, drag handle, scrim behind.
* Nested scrolling connects correctly to inner lists.
* Dismiss: drag > 40%, scrim tap (unless guarding a destructive action), back.
* **A11y:** `dialog()` semantics, focus moves into the sheet on open and returns on close,
  back closes it.

### `GlassDialog`
* Never dismissible by outside tap when confirming deletion.
* Destructive confirm button uses `error` colour **and** the word "Delete", never just red.

### `GlassContextMenu`
Anchored popup, grows from the anchor corner.
* Max 7 items; beyond that use a sheet.
* Destructive items are last, separated by a divider.
* **A11y:** `Role.DropdownList`, focus trapped, back dismisses.

### `GlassBreadcrumb`
Horizontally scrollable path. Root icon + segments, chevron separators.
* Auto-scrolls to the trailing end on navigation.
* Overflow: leading segments collapse into a `…` chip that opens a menu of ancestors.
* **A11y:** each segment is a button reading "Navigate to *Documents*"; the trailing segment
  is `heading()` and not clickable.

### `GlassProgressIndicator`
Linear and circular. The linear variant is a glass capsule whose fill is a gradient with a
slow specular sweep (Tier A only).
* Determinate values interpolate with `Motion.Gentle` — never jump.
* **A11y:** `progressSemantics(value, range)`; announcements throttled to every 10%.

### `GlassSegmentedControl`
Used for sort order, view mode, and search scope. Sliding glass indicator.
* **A11y:** `Role.RadioButton` per segment, `selectableGroup()` on the container.

### `GlassChip`
Filter chips in search and the quick-actions row. States: unselected, selected, dismissible.

### `GlassFab` / `ContextualToolCluster`
Primary action per screen (New folder in Browse, Scan in Storage). The cluster is a V1
expansion: long press on the FAB fans out 3 related actions with a staggered `Liquid` spring.

---

## Part 2 — File domain components

### `FileRow`
```kotlin
@Composable
fun FileRow(
    node: FileNodeUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    modifier: Modifier = Modifier,
)
```
* Leading: 44dp `FileThumbnail`. Title: name, `bodyLarge`, middle-ellipsis, 1 line.
  Subtitle: `size · date`, `labelMedium`, `onSurfaceVariant`. Trailing: overflow icon, or a
  checkbox in selection mode.
* **States:** default, pressed, selected, disabled (inaccessible file), pending (being moved),
  error (last operation failed on it).
* **Never glass.** Rows are opaque; selection uses `primaryContainer` with `shape.row`.
* **A11y:** the whole row is one node. `contentDescription` = "*report.pdf*, PDF, 1.2 MB,
  modified today"; `Role.Button`; `selected` state when in selection mode;
  `onLongClickLabel = "Select"`. The overflow icon is a separate 48dp node.
* **Performance:** stable `key` = node id; `FileNodeUi` is `@Immutable`; no lambda allocated
  per item (hoist with `remember`).

### `FolderRow`
As `FileRow` but the subtitle is "*n* items" and size resolves lazily (shows "—" then fills
in). A trailing chevron replaces the overflow when not in selection mode.

### `FileGridItem`
Square cell, thumbnail fill, name overlay on a bottom gradient (not glass), selection tick
top-trailing. Same semantics contract as `FileRow`.

### `FileThumbnail`
Coil-backed. Resolution order: MediaStore thumbnail → `ContentResolver.loadThumbnail`
(API 29+) → decode with `inSampleSize` → type icon.
* Never decodes above 2× the display size.
* Video shows a duration badge; APK shows the app icon; archives show a count when cached.
* **A11y:** `contentDescription = null` (the parent row describes it).

### `FileTypeIcon`
Glyph + category tint. 40 mapped extensions, then a MIME-family fallback, then generic.

### `StorageCard` / `StorageMeter`
* A single horizontal stacked bar segmented by category, with a legend beneath — not a pie,
  not a donut, not a treemap. Users compare lengths far better than angles.
* Free space is always the last, unfilled segment.
* **A11y:** the meter is one node reading "84.2 GB of 128 GB used. Images 32 GB, Video 21 GB…".
  Each legend entry is separately focusable and navigates to that category.
* Segment colours come from the colour-blind-safe category accents and each legend row also
  carries the category glyph.

### `FileOperationCard`
Shows: operation type, source → destination, item n of m, current file name, progress bar,
bytes and ETA, Pause (V1) / Cancel.
* **States:** queued, running, paused, completed, failed, cancelled, partially-completed.
* Partially-completed is a first-class state showing "18 of 20 copied · 2 failed" with a
  "View failures" action.
* **A11y:** progress is a live region announced at 10% intervals; Cancel has a confirm.

### `QuickAction`
Icon + label chip, 48dp tall, `GlassStyle.Control`.

### `EmptyState` / `ErrorState`
Glyph (64dp), headline (`titleMedium`), body (`bodyMedium`, ≤ 2 lines), optional action button.
`ErrorState` takes a `FileError` and renders the mapped message from `ERROR_MODEL.md`.

### `PermissionCard`
Explains one missing access in plain language, with a single primary button that launches the
correct flow for the current API level. Shows the *consequence* of not granting it, not a
lecture. Never dead-ends.

### `SelectionActionBar`
Glass bar replacing the nav bar in selection mode. 5 actions maximum: Copy, Move, Delete,
Share, More.

### `SkeletonList`
Shimmer-free placeholder rows matching real row geometry exactly, so nothing shifts on load.
Shimmer is deliberately omitted — it is motion that carries no information.

---

## Part 3 — Component checklist

Before a component is considered done:

- [ ] Previews for: light, dark, Tier A/B/C, font scale 2.0, RTL, and the longest realistic content
- [ ] Semantics verified with a Compose UI test asserting `contentDescription` / `Role` / `selected`
- [ ] All touch targets ≥ 48dp (verified by test)
- [ ] No hardcoded colour, dp, duration, or blur value
- [ ] Stateless: no `remember` holding business state, no ViewModel reference
- [ ] Renders correctly with the glass modifier removed entirely
- [ ] No recomposition per frame during scroll (verified with the recomposition counter)
