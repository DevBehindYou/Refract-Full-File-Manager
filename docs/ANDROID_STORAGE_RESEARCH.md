# Android Storage Research (API 27 → 37)

Research date: **August 2026**. Primary sources are Android developer documentation, the
Google Play Console policy pages, and AOSP behaviour-change notes. Where a behaviour is
device-dependent rather than documented, it is labelled **[device-variable]** and must be
handled by capability check, not by assumption.

---

## 1. Executive summary — the five facts that shape the architecture

1. **No single API covers API 27 → 37.** The app needs three interchangeable backends:
   direct `File` I/O, Storage Access Framework (`DocumentFile` / `DocumentsContract`), and
   `MediaStore`. They are selected at runtime per storage volume.
2. **Scoped Storage begins at API 29 and becomes mandatory at API 30.** `requestLegacy‑
   ExternalStorage` is honoured on API 29 only and ignored from API 30. Since we must
   target API 36, legacy mode is dead for us.
3. **`MANAGE_EXTERNAL_STORAGE` is the only way to be a real file manager on API 30+**, and
   Google Play gates it behind a Permissions Declaration Form. File managers are an
   explicitly permitted use, but the core functionality must be prominently documented in
   the store listing and visible in the app.
4. **Even with All Files Access, `/Android/data` and `/Android/obb` are off-limits** on API
   30+. Write access is blocked; on API 33+ the SAF picker also refuses to grant those
   trees. Do not build a feature that pretends otherwise.
5. **Removable volumes are always SAF territory.** The path may be visible via
   `StorageManager`, but write access to an SD card or USB OTG volume on API 29+ realistically
   requires a user-granted tree URI persisted across restarts.

---

## 2. Version-by-version behaviour

### API 27–28 (Android 8.1, 9 Pie)

* `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` are runtime permissions and, once
  granted, give broad read/write across the primary shared volume via `java.io.File`.
* Removable SD card: readable via `File` in most OEM builds; **writing requires a SAF tree
  URI** (this restriction dates to KitKat/Lollipop). **[device-variable]**
* `MediaStore` exists but is not required for access.
* No `RenderEffect`, no `RuntimeShader`, no `Modifier.blur` backing.
* `DocumentFile` and `ACTION_OPEN_DOCUMENT_TREE` are available (API 21+) and work.

**Backend choice:** `FileSystemBackend` for primary; `SafBackend` for removable writes.

### API 29 (Android 10)

* Scoped Storage introduced. Apps get a sandboxed view of external storage by default.
* `android:requestLegacyExternalStorage="true"` restores the old behaviour **only if the app
  targets 29 or lower**. Since we target 36, this is unavailable to us and must not be
  added to the manifest — it also triggers a Play warning.
* `MediaStore` becomes the sanctioned route to shared media; `MediaStore.Files` covers
  non-media on the primary volume.
* `MANAGE_EXTERNAL_STORAGE` does **not** exist yet (added in 30).
* Practical consequence: **API 29 is the hardest version to support.** A file manager on 29
  targeting 36 must operate through SAF for arbitrary directories and MediaStore for media.
  Plan for a "grant access to this folder" flow on 29, not an all-files toggle.

**Backend choice:** `SafBackend` (primary + removable) + `MediaStoreBackend` for the
category/search fast path.

### API 30 (Android 11)

* `MANAGE_EXTERNAL_STORAGE` ("All files access") added. Request path:
  * declare `<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />`
  * check `Environment.isExternalStorageManager()`
  * send the user to Settings with `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` (or
    `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` with a `package:` URI)
* What it grants: read/write across shared storage, access to the `MediaStore.Files` table,
  and access to the root of the SD card and USB OTG volume.
* What it does **not** grant: write access to `/Android/data/` and `/Android/obb/`, or to
  other apps' internal data directories. `/sdcard/Android/media` remains part of shared
  storage and is accessible.
* `requestLegacyExternalStorage` ignored.
* Raw paths become usable again for apps holding the permission — but always verify with a
  probe write rather than assuming.

**Backend choice:** `FileSystemBackend` when All Files Access is granted, otherwise
`SafBackend`.

### API 31–32 (Android 12, 12L)

* Storage semantics unchanged from 30.
* Graphics: `RenderEffect` added (31) — `createBlurEffect`, `createRuntimeShaderEffect`
  (the latter only useful with `RuntimeShader`, which is 33). `Modifier.blur` in Compose is
  backed by `RenderEffect` and is effectively **API 31+ only**.
* API 31 blur has known artefacts under certain layer configurations; the Haze library
  historically avoided the `RenderNode` blur path on 31 specifically. Treat 31 as
  "blur available but conservative".
* Splash screen API, approximate location, exact alarm restrictions — none storage-critical
  but relevant to onboarding.

### API 33 (Android 13)

* **Granular media permissions replace `READ_EXTERNAL_STORAGE`:**
  `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`.
  `READ_EXTERNAL_STORAGE` no longer has effect for apps targeting 33+.
* `POST_NOTIFICATIONS` becomes a runtime permission — required for our foreground-service
  operation notifications.
* SAF tightening: `ACTION_OPEN_DOCUMENT_TREE` refuses to return `/Android/data` and
  `/Android/obb` trees (and the Download root has long been non-selectable as a whole on
  some versions **[device-variable]**).
* **`RuntimeShader` + AGSL added.** This is the earliest API where true background-sampling
  refraction is possible. It is the boundary for Glass Tier A.

### API 34 (Android 14)

* `READ_MEDIA_VISUAL_USER_SELECTED` — users can grant partial photo/video access. If the app
  requests `READ_MEDIA_IMAGES`/`VIDEO` and the user picks "Select photos", only selected
  items are visible. A file manager holding All Files Access is unaffected, but the
  **fallback path must handle a partial media grant gracefully**.
* Foreground service types are mandatory and enforced. Our file-operation service declares
  `dataSync` (with `shortService` as a fallback consideration for brief work).
* Predictive back becomes opt-in-and-recommended.

### API 35 (Android 15)

* **Edge-to-edge enforced** for apps targeting 35+: `enableEdgeToEdge()` and correct
  `WindowInsets` handling are mandatory, not optional. This directly shapes the floating
  glass bottom bar, which must sit above `navigationBars` insets.
* 16 KB memory page size compatibility work begins for native libraries (affects any native
  archive/hash library we might link).
* Private Space — a separately authenticated profile. Files in it are simply not visible to
  us; no special handling beyond not crashing on unexpected volume states.

### API 36 (Android 16) — our target

* Edge-to-edge cannot be opted out of.
* **Predictive back is on by default** for apps targeting 36. `onBackPressed()` is
  effectively dead; use `OnBackPressedCallback` / `PredictiveBackHandler` in Compose.
* Large-screen resizability is enforced on large-screen devices — the app must not lock
  orientation or assume a fixed window size.
* `MediaStore#getVersion()` is now per-app-unique — do not use it as a global cache key
  across apps, only as an internal invalidation token.
* Photo picker pre-selects app-owned media when the user chooses limited access.
* Tighter `JobScheduler` quotas — another reason long copies live in a **foreground service**,
  not a background job.

### API 37 (Android 17)

* Stable around mid-2026, API level 37. No storage-model overhaul known at time of writing;
  continued tightening of background execution and audio focus.
* Play requirement today is target 36; 37 becomes required a year after its release.
  **Action:** compile against 36, add a CI job that also compiles against the latest
  available SDK so drift is caught early.

---

## 3. Capability matrix

| Capability | 27 | 28 | 29 | 30 | 31 | 32 | 33 | 34 | 35 | 36 | 37 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Broad `File` I/O without SAF | ✅ | ✅ | ❌¹ | ⚠️² | ⚠️² | ⚠️² | ⚠️² | ⚠️² | ⚠️² | ⚠️² | ⚠️² |
| `MANAGE_EXTERNAL_STORAGE` | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SAF tree URIs | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅³ | ✅³ | ✅³ | ✅³ | ✅³ |
| `MediaStore.Files` (non-media) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Granular media permissions | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/Android/data` write | ✅ | ✅ | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `RenderEffect` (blur) | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `RuntimeShader` / AGSL | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Predictive back | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | opt-in | opt-in | opt-in | default | default |
| Edge-to-edge enforced | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| `POST_NOTIFICATIONS` runtime | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| FGS type required | ❌ | ❌ | ✅⁴ | ✅⁴ | ✅⁴ | ✅⁴ | ✅⁴ | ✅ | ✅ | ✅ | ✅ |

¹ Unless targeting ≤29 with `requestLegacyExternalStorage`, which we cannot do.
² Only with `MANAGE_EXTERNAL_STORAGE` granted, and never into `/Android/data|obb`.
³ Cannot select `/Android/data`, `/Android/obb`, and some roots. **[device-variable]**
⁴ `foregroundServiceType` attribute exists from 29; *enforcement* lands in 34.

---

## 4. Storage locations and how to reach each one

| Location | API 27–28 | API 29 | API 30+ (All Files) | API 30+ (no All Files) |
|---|---|---|---|---|
| App internal (`filesDir`) | `File` | `File` | `File` | `File` |
| App external (`getExternalFilesDir`) | `File` | `File` | `File` | `File` |
| Primary shared (`/sdcard`) | `File` + perm | SAF / MediaStore | `File` | SAF tree |
| `Downloads` | `File` | MediaStore / SAF | `File` | SAF tree |
| SD card | read `File`, write SAF | SAF | `File` (root accessible) | SAF tree |
| USB OTG | OEM-dependent | SAF | `File` via mount point ⚠️ | SAF tree |
| `/Android/media/<pkg>` | `File` | `File`ish | `File` | limited |
| `/Android/data`, `/obb` | `File` | partial | **read-only/blocked** | **blocked** |
| Other apps' internal | ❌ | ❌ | ❌ | ❌ |
| `/`, `/system`, `/data` | ❌ (no root) | ❌ | ❌ | ❌ |

**Rule:** enumerate volumes with `StorageManager.storageVolumes`, never by hardcoding
`/storage/emulated/0` or scanning `/storage/*`. Use `StorageVolume.directory` (API 30+),
falling back to reflection-free heuristics on older versions, and always verify with a
probe read before showing a volume as browsable.

---

## 5. USB OTG

* `StorageVolume.isRemovable` plus `UsbManager` attach/detach broadcasts identify OTG media.
* On API 29+, treat OTG exactly like an SD card: request a tree URI, persist it.
* `ACTION_USB_DEVICE_DETACHED` must **cancel in-flight operations on that volume** and show a
  non-blocking error, never crash. This is a mandatory test case.
* Some OEMs never surface OTG as a `StorageVolume`. **[device-variable]** Degrade by falling
  back to whatever `ACTION_OPEN_DOCUMENT_TREE` offers.

---

## 6. MediaStore notes

* Fast for: category browsing (Images/Video/Audio/Documents/Downloads), recent files,
  "largest files" queries, and search by name across the primary volume. It is an index —
  use it as one.
* Unreliable for: freshly created files (until scanned), files outside indexed volumes,
  and anything on a removable volume that has not been scanned. Always reconcile with a
  filesystem/SAF check before acting on a MediaStore result.
* Writing via MediaStore requires `IS_PENDING` handling and, on 29+, `RELATIVE_PATH`.
  We use MediaStore for **reads and deletes of media only**; all general writes go through
  the File or SAF backend.
* Deleting another app's media on API 30+ requires `MediaStore.createDeleteRequest()` and a
  user confirmation dialog — implement it, do not swallow the `RecoverableSecurityException`.

---

## 7. Google Play policy for file managers

* All Files Access requires a **Permissions Declaration Form** in Play Console and passes
  review only when the permission is tied to the app's **core functionality**, and that
  functionality is prominently documented in the store listing.
* File managers are within the documented permitted uses. Our listing must therefore say,
  in plain language, that the app manages files across device storage.
* The app must show an in-app rationale before sending the user to the system settings page,
  and must remain usable (SAF mode) if the user declines. **Never dead-end the app on a
  refused permission.**
* Do not ship `requestLegacyExternalStorage`; it triggers policy warnings and does nothing
  at target 36.

---

## 8. Background work for long operations

| Option | Verdict |
|---|---|
| Plain coroutine in ViewModel | ❌ dies with the screen |
| `WorkManager` | ⚠️ correct for deferrable, restartable work; poor for a user-initiated copy that must report byte-level progress immediately and be cancellable instantly |
| Foreground service | ✅ correct for user-initiated, user-visible, must-finish-now work |
| **Hybrid (chosen)** | ✅ Foreground service (`dataSync` type) hosts a coroutine-based executor; `WorkManager` is used only for opportunistic maintenance (index refresh, thumbnail cache trim) |

Rationale in [`architecture/FILE_OPERATIONS.md`](architecture/FILE_OPERATIONS.md).
API 36's tighter JobScheduler quotas make the foreground service choice more, not less,
correct.

---

## 9. Things that must never appear in this codebase

* `requestLegacyExternalStorage`
* Hardcoded `/sdcard`, `/storage/emulated/0`, `/mnt/sdcard`
* Reflection to reach `StorageVolume.getPath()`
* Claims of "root access" or `/Android/data` browsing on API 30+
* `File.readBytes()` / `readText()` on a user-supplied file of unknown size
* `MediaStore` writes for arbitrary non-media files
* A permission request without a preceding in-app rationale screen

---

## 10. Open questions to verify on real devices

1. Which OEMs expose USB OTG as a `StorageVolume` on API 30+? **[device-variable]**
2. Behaviour of `ACTION_OPEN_DOCUMENT_TREE` on the Download root across OEM skins.
3. Whether `Environment.isExternalStorageManager()` is ever true on a work profile without
   the Settings toggle being reachable.
4. AGSL shader compilation time on low-end Mali/Adreno parts — feeds the Tier A/B threshold.

Each is a task in [`testing/DEVICE_MATRIX.md`](testing/DEVICE_MATRIX.md).
