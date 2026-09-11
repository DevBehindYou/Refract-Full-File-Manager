# Responsive Design

The phone layout is never stretched. Each window size class gets a layout designed for it.

## 1. Window size classes

| Class | Width | Devices | Navigation | Content |
|---|---|---|---|---|
| **Compact** | < 600dp | Phones portrait, folded | Material 3 bottom bar | Single pane |
| **Medium** | 600–839dp | Tablets portrait, phones landscape, unfolded portrait | Navigation rail (leading) | Single pane, wider gutters, grid density +1 |
| **Expanded** | ≥ 840dp | Tablets landscape, unfolded landscape, desktop windows | Navigation rail, optionally expanded with labels | **Dual pane** |

Height classes matter too: compact height (< 480dp, phone landscape) collapses the top bar
into a single 48dp row and hides the storage card on Home.

Use `currentWindowAdaptiveInfoV2()` from `material3-adaptive 1.3.0+`. Never read
`Configuration.screenWidthDp` directly, and never branch on `isTablet`.

## 2. Dual pane (expanded)

```text
┌──────┬─────────────────────┬──────────────────────────────┐
│ rail │  list pane (360dp)  │  detail pane (fills)         │
│      │  folders + files    │  preview / info / operations │
└──────┴─────────────────────┴──────────────────────────────┘
```

* List pane keeps the breadcrumb and the sort/view controls.
* Detail pane shows preview when a file is selected, folder info when a folder is selected,
  and an empty state otherwise ("Select a file to preview").
* Uses `ListDetailPaneScaffold`, so back behaviour and predictive back are handled correctly.
* A **persistent folder tree** appears as a third leading pane above 1200dp, collapsible.
* Selection mode replaces the detail pane's content with a batch summary rather than opening
  a bottom bar.

## 3. Foldables

| State | Behaviour |
|---|---|
| Folded (compact) | Standard phone layout |
| Unfolded (expanded) | Dual pane; the currently open file becomes the detail pane |
| Half-opened, book posture | Hinge-aware: list pane on the left of the hinge, detail on the right, with the hinge as the divider (`WindowLayoutInfo` fold bounds) |
| Half-opened, tabletop posture | List pane in the top half, actions in the bottom half |
| Fold/unfold transition | State preserved exactly; the folder, scroll position, and selection survive |

Never place an interactive control under the hinge occlusion area.

## 4. Multi-window and free-form

* API 36 forbids opting out of resizability on large screens — the app must handle any
  window size, including 300×400dp free-form windows.
* Below 320dp width, the grid drops to 2 columns and the bottom bar hides labels.
* Below 400dp height, the top bar collapses and the FAB becomes a small variant.
* No `android:resizeableActivity="false"`, no orientation lock, no fixed aspect ratio.

## 5. Adaptive parameters

| Parameter | Compact | Medium | Expanded |
|---|---|---|---|
| Screen gutter | 16dp | 24dp | 32dp |
| Grid min cell | 112dp | 128dp | 144dp |
| List row height | 64dp | 68dp | 68dp |
| Max content width | full | full | 720dp per pane, centred |
| Navigation | bottom bar | rail (icons) | rail (icons + labels) |
| Bottom sheet | full width | 640dp centred | side sheet in the detail pane |
| Dialog max width | 320dp | 480dp | 560dp |
| Columns (grid) | adaptive ≥ 3 | adaptive ≥ 4 | adaptive ≥ 6 |

## 6. Drag and drop (V1)

* Within the app: drag a file row onto a folder row or onto the other pane to move.
  Drop targets highlight with a 2dp `primary` border and a glass tint.
* Across apps: implement `DragAndDropTarget` accepting `ClipData` with `content://` URIs, and
  `dragAndDropSource` emitting `FileProvider` URIs with read permission. This is the main
  reason to support large screens properly — it is how files move on a tablet.
* Drag must be cancellable by dropping outside any target; nothing moves without a valid drop.

## 7. Input

* Pointer (mouse/trackpad) support: hover states on rows, right-click opens the context menu,
  `Modifier.onPointerEvent` for secondary click.
* Physical keyboard shortcuts as listed in `ACCESSIBILITY.md` §6.
* Stylus: treated as touch; no stylus-only affordances.

## 8. Glass on large screens

* The navigation rail is glass, using `GlassStyle.Navigation` with `shape.card` rather than
  `pill`.
* The glass coverage cap (25% of viewport) is easier to meet on large screens, so Tier A is
  more often achievable — but the backdrop capture area is larger, so cost scales with pixels.
  `GlassCapabilityManager` therefore factors in window area, not just device class.
* Never put glass on both panes' toolbars simultaneously; the shared toolbar is one surface.

## 9. Testing

* Screenshot tests at: 360×640, 411×891, 600×960, 840×1280, 1280×800, 1600×1000, and
  300×400 (free-form minimum).
* Fold/unfold state-preservation test on a foldable emulator profile.
* Rotation and multi-window resize tests on every screen.
