# UI/UX Guidelines

## 1. The seven interaction principles

1. **One primary action per surface.** If two actions look equally important, one is wrong.
2. **Destructive actions are never one tap from rest.** Delete lives behind selection mode
   or an overflow, is confirmed, and is undoable where the platform allows.
3. **Long press opens depth.** Long press on a row enters selection mode and reveals the
   contextual tools. Nothing critical is *only* behind long press.
4. **Never block on I/O.** Every list paints structure in one frame and fills in.
5. **Motion explains hierarchy.** A folder opens forward; back reverses the same transform.
   Sheets rise. Menus grow from their anchor. Nothing fades in from nowhere.
6. **Glass marks what floats.** If it is glass, it is chrome, it is above the content plane,
   and it is interactive.
7. **Say the true thing.** "Android does not allow apps to open this folder" beats
   "Something went wrong".

## 2. Information architecture

```text
Home ─────────► category / recent / favourite  ──► Folder ──► Preview
Browse ───────► volume ──► Folder (recursive) ──► Preview
Search ───────► results ──► Folder | Preview
Storage ──────► breakdown ──► filtered list ──► Folder | Preview
More ─────────► Archives | Operations | Favourites | Settings | About
```

Navigation depth to any MVP feature is **≤ 3** from Home. Verified in
`screens/SCREEN_INDEX.md`.

## 3. Home layout order (and why)

1. **Storage meter card** — answers "how bad is it" instantly; the single most-asked question.
2. **Quick actions row** — 4 chips: New folder, Search, Recents, Analyse.
3. **Category grid** — 8 tiles, 4×2. The beginner's entire mental model.
4. **Favourites** — horizontal row of pinned locations, only if non-empty.
5. **Recent files** — 6 rows plus "See all".
6. **Volumes** — internal / SD / USB with free space.

The filesystem is not on Home. It is one tap away in Browse. This is deliberate: Priya never
needs a path, and putting `/storage/emulated/0` above the fold makes the app feel technical.

## 4. Glass usage rules

**Glass is applied to exactly these surfaces:**

* Bottom navigation bar
* Top toolbar when it floats over scrolled content
* Search bar
* Bottom sheets, dialogs, context menus
* Selection action bar
* FAB and the contextual floating tool cluster
* Snackbars / operation pill

**Glass is never applied to:**

* List rows, grid cells, thumbnails
* The app background
* Any container whose primary job is holding body text
* More than two stacked layers
* Anything inside a scrolling list

**Coverage cap:** ≤ 25% of the viewport. When a sheet is open, the bottom bar's glass is
suppressed to a scrim so the two do not stack.

## 5. Density and hit targets

* Every tappable element is ≥ 48dp, even when its visual is 24dp.
* List rows are 64dp (72dp with thumbnail); grid cells 112dp minimum.
* Adjacent independent targets are separated by ≥ 8dp.
* The checkbox in selection mode occupies the leading 48dp of the row; the rest of the row
  toggles selection too.

## 6. Selection mode

* Entered by long press on any row, or by the "Select" overflow item.
* The top bar becomes a count ("3 selected") with Select all / Deselect all.
* A **glass action bar** rises from the bottom, replacing the nav bar: Copy, Move, Delete,
  Share, More.
* Exit via back, X, or deselecting the last item.
* Selection survives scrolling, sorting, and rotation. It does **not** survive navigating
  into a different folder (with one V1 exception: the clipboard).

## 7. Empty and error states

Every list has three non-content states, and all three are designed, not defaulted:

| State | Contains |
|---|---|
| **Loading** | Skeleton rows matching the real row geometry. Never a centred spinner on a list. |
| **Empty** | Glyph, one-line headline, one-line explanation, and a primary action where one exists ("Create folder") |
| **Error** | Glyph, plain-language cause, and the specific fix ("Grant access to SD card") |

Permission-blocked states are a distinct fourth case and always offer the grant path.

## 8. Copy style

* Sentence case everywhere. No title case, no ALL CAPS.
* Second person, present tense: "Grant access", not "Granting access".
* Never expose: URI, MIME, SAF, scoped storage, `content://`, exception names.
* Sizes: `1.2 GB`, `840 MB`, `12 KB` — one decimal above 1 GB, none below.
* Dates: "Today 14:32", "Yesterday", "12 Mar", "12 Mar 2024" for other years.
* Errors name the object and the fix: "Couldn't copy *report.pdf* — the SD card was removed."

## 9. Gestures

| Gesture | Action | Notes |
|---|---|---|
| Tap | Open folder / preview file | |
| Long press | Enter selection, select item | Haptic: `LONG_PRESS` |
| Long press + drag | Multi-select by drag (V1) | |
| Drag item onto folder | Move (V1, tablets and phones) | Requires a clear drop target highlight |
| Swipe down on list | Refresh | |
| Swipe on breadcrumb | Scroll path | |
| Back gesture | Up one level, then out of the app | Predictive back previews the destination |
| Pinch in image viewer | Zoom | |
| Two-finger swipe | Nothing | Reserved; never assign critical actions to it |

**Forbidden:** swipe-to-delete on a file row. The cost of an accidental swipe is data loss.

## 10. Feedback rules

* Any action taking > 200 ms shows progress.
* Any action taking > 2 s moves to the operation queue with a notification.
* Success is a snackbar with an Undo affordance where applicable, not a dialog.
* Haptics: light tick on selection, medium on operation completion, error pattern on failure.
  All haptics respect the system haptic setting.

## 11. What we will not do

* No onboarding carousel. Permission setup *is* the onboarding, and it is three screens.
* No interstitial "rate us" or "upgrade" prompts.
* No hidden gestures carrying unique functionality.
* No more than 5 items in any bottom sheet's primary action group.
* No nested bottom sheets.
* No modal dialog that can be dismissed by tapping outside when it guards a destructive action.
