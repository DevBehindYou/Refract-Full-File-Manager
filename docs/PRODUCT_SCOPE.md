# Product Scope

## 1. In scope — MVP

* Browsing internal shared storage and SD card
* Category browsing (Images, Video, Audio, Documents, Archives, APKs, Downloads)
* Recent files, Favourites / pinned folders
* Search: filename, extension, category, size, date — scoped to a folder tree or all storage
* File operations: copy, move, rename, delete, share, open-with, new folder, new file
* Multi-select and batch operations, with an operation queue and progress
* Storage overview: used/free, by category, largest folders and files
* Preview: image, video, audio, plain text/code/markdown/json
* ZIP: create and extract
* Hidden-file toggle, sorting, view mode (list/grid), folder size calculation
* Full permission flows for API 27 → 37
* Light and dark theme, three-tier Liquid Glass rendering
* Full TalkBack support, reduce motion/transparency, font scaling to 200%

## 2. In scope — V1

* Room-backed search index with incremental scanning
* Duplicate file finder (size → partial hash → full hash)
* Large files and empty folders views
* Command palette
* Undo for move/rename/delete-to-trash
* Clipboard manager and folder history
* USB OTG support
* Dual-pane on tablets and unfolded foldables, navigation rail, drag and drop
* PDF preview, APK metadata, checksums (MD5/SHA-1/SHA-256)
* TAR support, ZIP encryption read
* Home screen customisation (which shortcut rows appear)

## 3. Out of scope — permanently or until proven

| Item | Status | Reason |
|---|---|---|
| Root / system partition browsing | Out | Play policy risk, safety risk, tiny audience |
| FTP / SMB / WebDAV / SFTP client | Future | Large surface area, security burden |
| FTP/HTTP **server** | Out | Security liability |
| Cloud accounts (Drive, Dropbox) | Future | Requires accounts, breaks local-first promise |
| Built-in media player beyond preview | Out | Hand off via `Intent` |
| Document editing (docx/xlsx) | Out | Rendering fidelity is a product in itself |
| Themes marketplace, icon packs | Out | Dilutes the design identity |
| Ads, analytics SDKs, referral | Out | Contradicts `PRIVACY.md` |
| Cloud AI features on user files | Out | Contradicts `PRIVACY.md`; see `roadmap/FUTURE.md` §AI |
| `/Android/data` browsing on API 30+ | Impossible | Platform-blocked; see `ANDROID_STORAGE_RESEARCH.md` |

## 4. Non-goals (design)

* Not a "pro tool" aesthetic. No dense toolbars, no permanent 8-icon action row.
* Not a category-only launcher that hides the filesystem. Both views are first class.
* Not maximal glass. Glass is chrome only, capped at 25% of the viewport.
* Not a settings maze. Settings fits on two scroll-screens.

## 5. Scope guards

Any new feature request is tested against three questions:

1. Does it serve Priya, Arjun, Sana or Dev specifically?
2. Can it be reached in ≤ 3 taps from Home without adding a permanent UI element?
3. Does it risk file safety, and if so, is the risk contained by confirmation + undo?

Two "no"s and it goes to `roadmap/FUTURE.md`.
