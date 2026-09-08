# Accessibility

Accessibility is written at the same time as the component, not audited in afterwards. A
component without semantics is not finished.

**Standard: WCAG 2.2 AA**, plus Android-specific requirements below.

---

## 1. TalkBack

### Labelling rules

| Element | Label |
|---|---|
| File row | "*report.pdf*, PDF document, 1.2 megabytes, modified today at 14:32" |
| Folder row | "*Documents*, folder, 42 items" (or "folder" alone if the count is not yet computed) |
| Thumbnail inside a row | `null` — the row describes it |
| Nav item | Label + `Role.Tab` + `selected` state |
| Breadcrumb segment | "Navigate to *Documents*" |
| Overflow icon | "More options for *report.pdf*" |
| Selection checkbox | Handled by the row's `selected` state, not a separate announcement |
| Storage meter | Full sentence with the top three categories |
| Progress | `progressSemantics()` + live region, throttled to 10% steps |
| Glass surfaces | No semantics contribution whatsoever |

### Traversal

* Content before chrome: list → toolbar → bottom bar.
* `traversalIndex` used only where the visual order genuinely differs from the DOM order
  (the floating bottom bar).
* Sheets and dialogs trap focus (`Modifier.semantics { dialog() }` / `paneTitle`), move
  focus in on open, and restore it on close.
* Selection mode announces the transition once: "Selection mode. 1 item selected."
  Subsequent selections announce only the count.

### Custom actions

Rows expose `customActions` so TalkBack users reach Copy / Move / Delete / Share / Info
without long press:
```kotlin
Modifier.semantics {
    customActions = listOf(
        CustomAccessibilityAction("Copy") { onCopy(); true },
        CustomAccessibilityAction("Move") { onMove(); true },
        CustomAccessibilityAction("Delete") { onDelete(); true },
        CustomAccessibilityAction("Share") { onShare(); true },
        CustomAccessibilityAction("File info") { onInfo(); true },
    )
}
```
This is mandatory on `FileRow`, `FolderRow`, and `FileGridItem`.

## 2. Contrast

* Body text ≥ 4.5:1, large text and meaningful icons ≥ 3:1.
* **Glass rule:** contrast is measured against the *worst-case* backdrop, not the average.
  Every glass container that holds text carries an internal opaque tint layer sized to
  guarantee the ratio. This layer is part of the component, not optional.
* High-contrast text setting → borders go to 1.5dp, `outline` uses `onSurface` at 0.6,
  glass drops to Tier C.
* Colour is never the only signal: category colour is paired with a glyph, selection is
  paired with a checkbox, errors are paired with an icon and text.

## 3. Motion and transparency

| System setting | Effect |
|---|---|
| Reduce motion / animator scale 0 | See `ANIMATION_SYSTEM.md` §7 — springs become 120 ms opacity tweens, no translation or scale, no ambient glass drift |
| Reduce transparency / high contrast | `GlassTier.COMPATIBILITY`, all glass opacity → 1.0, blur → 0 |
| Bold text | Body weights +100 |
| Remove animations (developer options) | Same as reduce motion |

The app also exposes its own **Glass: Auto / High / Medium / Off** setting so a user can opt
out without changing a system-wide preference.

## 4. Text scaling and display size

* Layout must survive **font scale 2.0 × display size largest** with no clipped or
  overlapping text. Verified by screenshot test on every screen.
* Fixed-height rows become `IntrinsicSize.Min` above font scale 1.3.
* The bottom bar hides labels above font scale 1.8 and grows to 72dp, keeping icons legible.
* Never `maxLines = 1` on anything but a file name (which uses middle ellipsis and exposes
  the full name to TalkBack and via long press).
* No `sp`-to-`dp` conversion hacks; no `nonScaledSp`.

## 5. Touch targets

* Every interactive element ≥ 48×48dp, even when the drawn glyph is 24dp
  (`Modifier.minimumInteractiveComponentSize()`).
* ≥ 8dp between independent targets.
* Drag handles ≥ 48dp tall.
* Verified by a Compose test rule that walks the semantics tree and asserts bounds.

## 6. Keyboard and D-pad (V1, designed from the start)

| Key | Action |
|---|---|
| Tab / Shift-Tab | Focus traversal |
| Arrows | Move within lists and grids |
| Enter | Open |
| Space | Toggle selection |
| Ctrl+A | Select all |
| Ctrl+C / X / V | Copy / cut / paste |
| Delete | Delete (with confirmation) |
| F2 | Rename |
| Ctrl+F | Search |
| Ctrl+K | Command palette |
| Backspace / Esc | Up one level / exit selection |

Focus is always visible: a 2dp `primary` ring with 2dp offset, on every focusable element.
No focus trap outside intentional dialogs.

## 7. Large screens

* Never stretch a phone layout. See `RESPONSIVE_DESIGN.md`.
* Navigation rail replaces the bottom bar at expanded widths, and it is reachable first by
  keyboard.
* Dual-pane announces pane titles (`Modifier.semantics { paneTitle = "Folder contents" }`) so
  TalkBack users can distinguish them.

## 8. Testing

| Check | How |
|---|---|
| Every interactive node has a label | Compose test walking the semantics tree, fails on missing `contentDescription`/text |
| Touch target sizes | Automated bounds assertion |
| Contrast | Screenshot test + a token-level unit test computing ratios for every text-on-surface pair |
| Font scale 2.0 | Screenshot tests at scales 1.0 / 1.3 / 2.0 |
| Reduce motion / transparency | Screenshot tests with the flags forced |
| TalkBack flows | Manual script per release: browse, select, copy, delete, search, preview |
| Accessibility Scanner | Run on every screen before each release; zero critical findings |

## 9. Non-negotiables

1. Liquid Glass never reduces text contrast below 4.5:1. If it would, the tier drops.
2. No functionality is reachable only by gesture.
3. No information is conveyed by colour, motion, or glass alone.
4. Every error state states the cause and the fix in plain language.
5. Every destructive confirmation is reachable and operable with a screen reader.
