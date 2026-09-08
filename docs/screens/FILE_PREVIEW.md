# Screen — File Preview (host, image, video, audio, document)

## 1. Purpose

Open a file without leaving the app when we can do it safely and well, and hand off to a real
app when we cannot. Preview is a **host** screen that picks a renderer from the resolved MIME
type. The host owns the chrome, navigation, and actions; renderers own only their content.

The rule that governs this whole screen: **we are a file manager, not a media player.**
We render enough to identify a file and act on it. Anything beyond that is `ACTION_VIEW`.

## 2. Host screen

```text
┌────────────────────────────────────────┐
│  ‹  IMG_4821.jpg                 ⋮     │  ← GlassToolbar, auto-hides in immersive
├────────────────────────────────────────┤
│                                        │
│              [ renderer ]              │
│                                        │
├────────────────────────────────────────┤
│   ⤴      ⓘ      ⧉      🗑              │  ← GlassActionBar: share, info, copy, delete
└────────────────────────────────────────┘
```

* **Renderer selection** is by resolved MIME (`../architecture/DATA_LAYER.md` §6), never by
  extension alone. Extension is the tiebreaker when the MIME is `application/octet-stream`.
* **Horizontal paging** across siblings in the originating list, in the same sort order the
  user was looking at. The pager holds only the current index plus one on each side.
* The toolbar and action bar are one glass layer each — never more than two glass layers on
  screen, per `../LIQUID_GLASS_RESEARCH.md` §6.

```kotlin
data class PreviewUiState(
    val current: FileNodeUi? = null,
    val siblings: List<FileNodeId> = emptyList(),
    val index: Int = 0,
    val renderer: RendererKind = RendererKind.Unsupported,
    val chromeVisible: Boolean = true,
    val loadState: PreviewLoadState = PreviewLoadState.Loading,
    val error: FileError? = null,
)

enum class RendererKind { Image, Video, Audio, Text, Pdf, Archive, Apk, Unsupported }
```

## 3. Image viewer

* **Loading:** Coil, decoded at the display size, never the source size. A 108 MP JPEG is
  ~324 MB decoded at full size and will OOM — `ImageRequest.size(Size.ORIGINAL)` is banned
  here.
* **Progressive:** thumbnail from MediaStore (or a downsampled decode) paints first, the full
  decode cross-fades over it in 120 ms.
* **Gestures:** pinch zoom 1×–8×, double-tap toggles 1× ↔ 3× at the tap point, pan when
  zoomed, swipe down to dismiss with the background alpha tracking the drag. Horizontal paging
  is disabled while zoomed above 1× so panning does not steal into a page change.
* **Formats:** JPEG, PNG, WebP, GIF (animated, via Coil's decoder), HEIF (API 28+), AVIF
  (API 31+). Below those levels the renderer falls back to Unsupported with an Open-with
  action rather than showing a broken image. RAW (DNG/CR2/NEF) is **[device-variable]** —
  attempt the decode, fall back cleanly on failure.
* **EXIF:** orientation is applied on decode. The info sheet shows camera, lens, ISO, exposure,
  and location **only if present**; location is shown as coordinates with a "this file
  contains location data" note, since that is a privacy-relevant fact about the user's file.

## 4. Video preview

* **Media3 ExoPlayer**, one player instance held by the ViewModel, released in `onCleared` and
  paused in `ON_STOP`. A leaked player keeps the screen awake and drains the battery — this is
  the single most common bug in this screen.
* **Controls:** play/pause, scrub bar with a preview thumbnail on drag (V1), current/total
  time, mute, fullscreen, "Open in player" which hands off via `ACTION_VIEW`.
* **Playback:** no autoplay, sound off until the user taps play, `setKeepScreenOn` only while
  actually playing.
* **Unsupported codecs** are expected and normal. On a decoder error we do not show a stack
  trace: we show the frame we have (or the thumbnail), the message "This video needs another
  app to play", and an Open-with button. Codec support is **[device-variable]**.
* **Audio focus** is requested on play and abandoned on pause; a phone call pauses playback.

## 5. Audio preview

* Same ExoPlayer instance policy.
* **Layout:** album art (from embedded metadata, falling back to a generated gradient derived
  from the file name hash — deterministic, never random), title/artist/album, waveform-styled
  scrub bar, play/pause, skip ±10 s.
* **Metadata** via `MediaMetadataRetriever` on a background dispatcher; the retriever is
  closed in a `finally`, always.
* **Background playback:** none. This is a file manager. Leaving the screen stops playback.
  If the user wants background audio, Open-with hands off to a music app.

## 6. Document preview

| Type | MVP | V1 |
|---|---|---|
| Plain text, code, log, JSON, XML, CSV, Markdown | Native viewer | + syntax highlighting, + word wrap toggle |
| PDF | `PdfRenderer` (API 21+), page-by-page | + text selection, + search |
| Office (docx/xlsx/pptx) | Not previewed — icon + Open with | Unchanged; we will not ship a document engine |

**Text viewer limits:** load at most the first 2 MB into the view, with a footer saying the
file is truncated and offering Open-with. Reading a 400 MB log into a `String` is an OOM, and
the naive implementation always does exactly that. Encoding is detected (UTF-8, UTF-16 BOM,
falling back to ISO-8859-1); undecodable bytes render as `�` rather than throwing.

**PDF viewer:** `PdfRenderer` renders one page at a time to a bitmap sized to the viewport.
Pages are rendered lazily with a two-page lookahead and an LRU bitmap cache capped at 6 pages.
Encrypted PDFs throw on open — caught and surfaced as "This PDF is password protected" with
Open-with. `PdfRenderer` is not thread-safe: all access goes through a single dispatcher.

## 7. Unsupported / other types

* **APK:** parse with `PackageManager.getPackageArchiveInfo` to show app name, package,
  version, and icon. We show `versionName`, `versionCode`, minSdk, and permissions requested.
  We do **not** offer to install — that is the system installer's job via `ACTION_VIEW`.
* **Archive:** tapping an archive opens the archive manager (`SCREEN_INDEX.md` §18), not this
  screen.
* **Everything else:** file glyph, name, size, type, and two actions: Open with, Share. This
  is a designed state, not a failure state.

## 8. Interactions (host)

| Action | Result |
|---|---|
| Tap content | Toggle chrome (immersive) |
| Swipe left/right | Previous/next sibling |
| Swipe down | Dismiss with drag-tracked scrim |
| Back / predictive back | Return to origin, shared-element reversed |
| ⤴ Share | `ACTION_SEND` via FileProvider — never a raw `file://` URI |
| ⓘ Info | File information sheet (`SCREEN_INDEX.md` §15) |
| ⧉ Copy | Adds to clipboard, then destination picker |
| 🗑 Delete | Confirm → trash → advance to the next sibling, or pop if last |
| ⋮ Overflow | Open with, Rename, Move, Set as (images), Print (PDF/image), Properties |

## 9. Animations

* **Entry:** shared-element transition from the source thumbnail to the full image, 300 ms
  `Motion.Standard`. If no source bounds are available (deep link, search result off-screen),
  fall back to a fade — never a wrong-position shared element.
* **Chrome:** slides out top and bottom with a fade, 200 ms; auto-hides after 3 s of
  inactivity in image and video renderers only.
* **Paging:** standard pager physics, no parallax (it fights the zoom gesture).
* **Dismiss:** scale tracks drag distance to a floor of 0.85; releasing past 30% of the height
  or above a velocity threshold completes the dismiss, otherwise it springs back.
* All of the above respect reduce-motion: transitions become cross-fades, gesture tracking
  remains.

## 10. Edge cases

| Case | Behaviour |
|---|---|
| File deleted while open | "This file no longer exists" + pop |
| Corrupt image | Broken-file state, Open-with offered, no crash |
| 0-byte file | "This file is empty" — a designed state, not an error |
| Huge image (>50 MP) | Downsampled decode; the info sheet still shows true dimensions |
| Animated GIF > 20 MB | First frame only, with a "Play" action that hands off |
| No read access (SAF grant revoked) | `AccessDenied` state with a Grant action |
| Sibling list stale (item deleted elsewhere) | Skip it silently on page, do not crash the pager |
| Rotation | Zoom level and pan position preserved; player position preserved |
| Process death | `FileNodeId` and playback position restored from `SavedStateHandle` |
| Video with no audio track | Mute control disabled, not hidden |

## 11. Accessibility

* The image is described as "Image, IMG_4821.jpg" — we do not fabricate content descriptions
  of image contents.
* Zoom is available as custom actions (Zoom in / Zoom out / Reset) so it is not gesture-only.
* Player controls have labels and state ("Pause, playing"); the scrub bar exposes
  `ProgressBarRangeInfo` and supports `SetProgress`.
* Chrome auto-hide is **disabled** when a screen reader or switch access is active — hiding
  the only controls from someone who cannot tap-to-reveal is a trap.
* Text viewer respects the system font scale and allows selection.

## 12. Responsive

| Class | Layout |
|---|---|
| Compact | Full-screen, immersive |
| Medium | Full-screen; actions move to a rail on the trailing edge |
| Expanded | Preview fills the detail pane, list stays visible; arrow keys change the selection and the preview follows |
| Landscape video | Fills the display, chrome overlays; the cutout area is respected |
