# MVP Scope

## 1. The MVP test

> A person installs Refract, grants whatever access their Android version allows, browses every
> file they can reach, finds a file by name, previews it, copies it to another folder, deletes
> something into the trash, restores it, and sees what is filling their storage — without ever
> being confused about why something is not available.

If that sentence is true on API 27, 29, 30, 33, and 36, the MVP is done. Nothing else is a
release blocker.

## 2. In scope

**Storage and access**
* All three backends with automatic selection
* Volume enumeration: internal, SD, USB OTG, app-private
* Permission flows for every API level, with a usable app at every access level including none
* Permission troubleshooting screen

**Browsing**
* Home, Browse, folder browser with breadcrumb
* Sort (name / size / date / type, asc/desc, folders-first), list and grid views
* Hidden-file toggle, streaming listing for large folders
* Multi-selection with bulk actions
* Favourites, Recents, category screens
* File information sheet with on-demand checksums

**File operations**
* Copy, move, rename, delete, create folder, create file
* Foreground service, persistent queue, byte verification
* Conflict resolution with per-conflict and apply-to-all choices
* Trash with 7-day retention and restore; undo for copy, move, trash
* Progress via notification, inline banner, and operations sheet

**Search**
* Name search across all granted storage, three concurrent sources
* Filters: category, extension, size, date; scope selection
* Coverage warnings, recent queries, 5,000-result cap

**Preview**
* Image (with zoom/pan, EXIF), video, audio, text/code, PDF, APK info
* Unsupported-type state with Open-with and Share

**Storage analysis**
* Volume totals, category breakdown, largest folders and files, trash size
* Scan with progress and cancellation, cached results
* Large-file cleanup

**Archives**
* Browse inside ZIP; extract all or selected, with full path validation
* Create ZIP from a selection

**Design and quality**
* Full design system, all three glass tiers, dark mode, dynamic colour
* Full accessibility pass, responsive layouts to expanded width
* Local-only; base flavour has no network permission

## 3. Explicitly out of MVP

| Deferred | Where | Why |
|---|---|---|
| Cloud storage | Never | Contradicts the local-first premise |
| Duplicate finder | V1 | Correct duplicate detection needs hashing infrastructure and a very careful selection UI. Rushed, it deletes originals |
| Empty-folder cleanup | V1 | Same reason, smaller stakes |
| Drag and drop to move | V1 | Needs the operation engine proven first |
| Encrypted archives, RAR/7z | V1 | Format complexity; ZIP covers the common case |
| Office document preview | Never | We will not ship a document engine |
| Background audio playback | Never | We are a file manager |
| FTP / SMB / network shares | V1+ | Different security model, different error model |
| Root access | Never | Vast risk surface, tiny audience |
| Themes beyond dynamic colour | V1 | Cosmetic |
| File encryption / vault | V1+ | Key management done badly loses data permanently |
| Text editing (not just viewing) | V1 | Editing is a save path, and save paths lose data |
| Widgets, quick-settings tiles | V1 | Additive |
| Tablet three-pane | V1 | Two-pane is sufficient at MVP |

## 4. MVP quality bars

These are release blockers, not aspirations.

| Bar | Threshold |
|---|---|
| Data loss | Zero, across the full soak suite |
| Cold start | < 500 ms to first frame on mid-tier |
| Folder open, 1,000 items | < 300 ms |
| Search first result, 100k files | < 1 s |
| Crash-free sessions | > 99.5% in internal testing |
| TalkBack | Every screen fully operable, zero unlabelled controls |
| Font scale 200% | Every screen usable, nothing truncated |
| Glass off | App is complete and looks intentional |
| API coverage | Correct behaviour on 27, 29, 30, 33, 36 |

## 5. What "done" means for a feature

1. Requirements from `../FUNCTIONAL_REQUIREMENTS.md` are all satisfied and referenced by ID.
2. All four UI states designed and implemented — including no-access as distinct from empty.
3. Unit + integration + UI tests written and passing.
4. Accessibility verified with TalkBack on a device.
5. Behaviour verified on at least three API levels.
6. Errors produce plain-language messages, no codes, no exception names.
7. Works with glass disabled.
8. Survives rotation and process death.
