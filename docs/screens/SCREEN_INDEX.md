# Screen Index — all 25 screens

Seven high-complexity screens have dedicated files. The rest are fully specified here.
Every screen documents: purpose, layout, components, navigation, interactions, animations,
UI state, edge cases, accessibility, responsive behaviour.

| # | Screen | File | Release |
|---|---|---|---|
| 1 | Splash / startup | here §1 | MVP |
| 2 | Onboarding | here §2 | MVP |
| 3 | Permission setup | here §3 | MVP |
| 4 | Home | [`HOME.md`](HOME.md) | MVP |
| 5 | Browse (volumes) | [`BROWSE.md`](BROWSE.md) | MVP |
| 6 | Folder browser | [`BROWSE.md`](BROWSE.md) §4 | MVP |
| 7 | Search | [`SEARCH.md`](SEARCH.md) | MVP |
| 8 | Search results | [`SEARCH.md`](SEARCH.md) §4 | MVP |
| 9 | Storage overview | [`STORAGE.md`](STORAGE.md) | MVP |
| 10 | File preview host | [`FILE_PREVIEW.md`](FILE_PREVIEW.md) | MVP |
| 11 | Image viewer | [`FILE_PREVIEW.md`](FILE_PREVIEW.md) §3 | MVP |
| 12 | Video preview | [`FILE_PREVIEW.md`](FILE_PREVIEW.md) §4 | MVP |
| 13 | Audio preview | [`FILE_PREVIEW.md`](FILE_PREVIEW.md) §5 | MVP |
| 14 | Document preview | [`FILE_PREVIEW.md`](FILE_PREVIEW.md) §6 | MVP / V1 |
| 15 | File information | here §15 | MVP |
| 16 | Multi-selection mode | here §16 | MVP |
| 17 | Operation progress | [`OPERATIONS.md`](OPERATIONS.md) | MVP |
| 18 | Archive manager | here §18 | MVP |
| 19 | Favourites | here §19 | MVP |
| 20 | Recents | here §20 | MVP |
| 21 | Settings | [`SETTINGS.md`](SETTINGS.md) | MVP |
| 22 | About | here §22 | MVP |
| 23 | Permission troubleshooting | here §23 | MVP |
| 24 | Empty states | here §24 | MVP |
| 25 | Error states | here §25 | MVP |

---

## §1 Splash / startup

**Purpose:** cover the ~300 ms of cold start without a white flash, and route to onboarding
or Home.

* **Layout:** the system splash screen (`androidx.core.splashscreen`) with the app icon,
  extended by at most 400 ms while the access level is resolved.
* **Navigation:** → Onboarding (first run) | Permission setup (insufficient access) | Home.
* **Animation:** icon scales 1.0 → 0.92 → cross-fades into Home. Uses the platform exit
  animation API, not a custom Activity.
* **Edge cases:** never hold the splash longer than 400 ms — if access resolution is slow,
  go to Home and let it show skeletons. A splash that waits on I/O is a hang.
* **A11y:** the splash is not focusable; the first announced element is Home's title.

## §2 Onboarding

**Purpose:** two screens, then out of the way. Not a carousel.

* **Screen 1:** what the app does, one illustration, one line. "Every file on your phone,
  in one place."
* **Screen 2:** what it needs and what Android won't allow, honestly. Leads directly into §3.
* **Interactions:** Skip is always available and goes to Home in limited mode.
* **Edge cases:** shown once ever, keyed in DataStore. Reinstall shows it again; that is
  acceptable.
* **A11y:** no auto-advancing pages, no timed content.

## §3 Permission setup

**Purpose:** obtain the right access for the current API level, honestly.

* **Layout:** a `PermissionCard` per required `AccessStep`, in order, each with its own state
  (pending / granted / denied) and a single primary button.
* **Content varies by API level** — see `../architecture/PERMISSIONS.md` §1. The screen never
  contains its own `SDK_INT` branch; it renders the list `StorageAccessManager` gives it.
* **Interactions:** "Not now" is always present and equal in weight. After two denials of the
  same step, the button becomes "Open Settings".
* **Edge cases:** returning from the All Files Access settings page re-checks in `onResume`.
  If the user granted it, the card animates to granted and the screen auto-advances.
* **A11y:** each card is a heading + body + button; state changes are announced.
* **Responsive:** cards are max 560dp wide, centred on large screens.

## §15 File information

**Purpose:** everything known about one file.

* **Presentation:** bottom sheet on compact, side sheet in the detail pane on expanded.
* **Content:** name (editable inline), full path (monospace, copyable, long-press to copy),
  type + MIME, size (exact bytes in parentheses), created/modified/accessed dates,
  permissions (read/write/delete flags), containing folder (tappable), and for media:
  dimensions, duration, codec. Checksums (MD5/SHA-1/SHA-256) computed **on demand** behind a
  button, with progress, because hashing a 4 GB file is a real operation.
* **Actions:** Open, Share, Rename, Copy path, Add to favourites, Delete.
* **Edge cases:** unknown values show "—", never "null" or "0". A folder shows item count and
  a "Calculate size" button.
* **A11y:** each row is a label/value pair; the path is announced in segments, not as one
  unbroken string.

## §16 Multi-selection mode

**Purpose:** operate on many items.

* **Entry:** long press any row, or overflow → Select.
* **Layout:** top bar becomes "*n* selected" + Select all / Deselect all + close. The bottom
  nav is replaced by the glass `SelectionActionBar`: Copy, Move, Delete, Share, More.
* **Interactions:** tap toggles; long press + drag range-selects (V1); back exits.
* **Animation:** checkboxes slide in from the leading edge with a 12 ms stagger over the
  visible rows; the action bar rises as the nav bar drops.
* **State:** selection is a `Set<FileNodeId>` in `SavedStateHandle`, capped at 500 (beyond
  that, "Select all" still works but the set is stored as a range marker).
* **Edge cases:** actions disable when any selected item lacks the required `AccessFlags`,
  with a tooltip explaining which item. Selection survives rotation and sorting; it clears on
  navigating to a different folder.
* **A11y:** entry announced once; subsequent selections announce only the count. Every action
  in the bar has a label and is ≥ 48dp.

## §18 Archive manager

**Purpose:** look inside an archive and extract, without leaving the app.

* **Layout:** identical to the folder browser — breadcrumb, list, same row component — so
  there is nothing new to learn. A banner marks it as read-only archive content.
* **Interactions:** tap a folder to descend; tap a file to preview (extracted to cache first,
  with a size cap); select items → Extract; Extract all → destination picker.
* **State:** `ArchiveUiState(entries, path, isLoading, error, isEncrypted)`.
* **Edge cases:** corrupt archive → `CorruptedArchive` error state; entries failing the path
  check are listed in a "skipped for safety" section rather than silently dropped; encrypted
  archives prompt for a password (V1) or state that they are unsupported (MVP).
* **Security:** every extraction path is validated (`../architecture/SECURITY.md` §3).
* **A11y:** archive entries announce "in archive" so the context is never ambiguous.

## §19 Favourites

* **Purpose:** pinned files and folders.
* **Layout:** a single list, reorderable by long-press drag, grouped folders-first.
* **Edge cases:** a favourite whose target no longer exists renders dimmed with "Not found"
  and a Remove action — it is never silently deleted, because an SD card may return.
* **Empty state:** "Pin a folder to find it fast" with an illustration and a Browse button.
* **A11y:** drag reordering has keyboard/TalkBack equivalents (Move up / Move down custom
  actions).

## §20 Recents

* **Purpose:** recently modified and recently opened files.
* **Layout:** segmented control (Modified / Opened), then a list grouped by day
  (Today / Yesterday / This week / Earlier).
* **Source:** MediaStore for modified, Room for opened.
* **Edge cases:** with `MEDIA_ONLY` access, a banner explains that only media is shown.
* **A11y:** day headers are `heading()`.

## §22 About

* App name, version, build number, an honest one-paragraph description.
* Links: source repository, privacy policy, licences (`AboutLibraries`-style list).
* Diagnostics: Export diagnostics (see `../LOGGING_DIAGNOSTICS.md` §4), crash reporting toggle.
* No rate-us prompt, no social links, no newsletter.

## §23 Permission troubleshooting

**Purpose:** the screen a user reaches when something is inaccessible.

* Lists every access type with its current state and a fix action.
* Includes a plain-language explanation of the platform restrictions we cannot work around
  (`/Android/data` on API 30+, partial media grants on 34+).
* Includes a "Test access" button that probes each volume with a read and a write and reports
  the actual result rather than the theoretical one.
* **A11y:** each entry is a status + explanation + action, announced as a group.

## §24 Empty states

Every list has a designed empty state. Never a blank screen, never a bare spinner.

| Context | Headline | Body | Action |
|---|---|---|---|
| Empty folder | Nothing here | This folder is empty. | Create folder |
| No search results | No matches | Nothing matched "*query*". | Clear filters |
| No favourites | No pinned items | Pin a folder to find it fast. | Browse |
| No recents | Nothing recent | Files you open or change will appear here. | — |
| Empty category | No *images* | Nothing in this category yet. | — |
| Empty trash | Trash is empty | Deleted files stay here for 7 days. | — |
| No operations | No transfers | Copy or move files and you'll see progress here. | — |
| Empty archive | Empty archive | This archive has no files in it. | — |

## §25 Error states

Rendered from a `FileError` via `ErrorState` — see `../ERROR_MODEL.md` for the full mapping.

Structure is always: glyph, plain-language title naming the object, one-line cause, one
action. Never an error code, never an exception name, never "please try again later".

The four states that must be visually distinct and never confused:
**empty** (nothing here), **loading** (skeleton), **no access** (permission card), and
**error** (something failed). Conflating "no access" with "empty" is the highest-severity UX
bug in this app.
