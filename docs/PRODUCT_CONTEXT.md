# Product Context

## 1. The problem

Android's stock file access is fragmented. Most users encounter files through the Files by
Google app, an OEM "My Files", or a download notification. The third-party market splits
into two poles:

* **Simple but shallow** — clean UI, but no archive handling, weak search, no storage
  analysis, and a category grid that hides the actual filesystem.
* **Powerful but hostile** — root browsers, FTP servers, and eight-item toolbars, with UI
  that has not been redesigned in a decade and permission handling that breaks on new
  Android versions.

Almost nothing occupies the middle, and almost nothing looks *good*. File managers are a
category where visual craft has been abandoned because the work is unglamorous.

## 2. The opportunity

Two things have changed:

1. **Scoped Storage has stabilised.** The rules from API 30 onward are now well understood
   and unlikely to churn again soon. A file manager built correctly today has a long shelf
   life.
2. **The glass material moment.** Apple's Liquid Glass has reset expectations for what
   system-adjacent UI can look like. Android has had the primitives (`RuntimeShader`, AGSL)
   since API 33 and almost nobody is using them well.

A file manager is the ideal vehicle for a distinctive material because it is *chrome-heavy*:
navigation, toolbars, sheets and selection bars are constantly on screen over a scrolling
content plane. That is exactly where glass belongs.

## 3. Target users

| Persona | Description | What they need |
|---|---|---|
| **Priya, 24, non-technical** | Wants to find the PDF she downloaded and send it on WhatsApp | Download visible in one tap, share without menus, never sees the word "URI" |
| **Arjun, 31, prosumer** | Sideloads APKs, manages a 256 GB SD card, archives project folders | Multi-select, batch move, ZIP, storage analysis, fast search |
| **Sana, 38, storage-constrained** | 64 GB phone permanently full | Storage analyser, duplicate finder, largest-files view, safe bulk delete |
| **Dev, 27, developer** | Checks logs, moves builds, inspects APKs | Path breadcrumb, hidden files, text preview, checksums |

Priya is the design constraint. Arjun and Dev are the depth constraint. Sana is the
feature-differentiation constraint.

## 4. Competitive positioning

| | Files by Google | Solid Explorer | Total Commander | MiXplorer | **Refract** |
|---|---|---|---|---|---|
| Visual craft | Medium | Medium-high | Low | Low | **Very high** |
| Beginner friendly | High | Medium | Low | Low | **High** |
| Power features | Low | High | Very high | Very high | **Medium-high** |
| Modern stack | Partial | Partial | No | No | **Full Compose** |
| Storage analysis | Basic | Basic | No | Basic | **Core feature** |
| Local-first / no account | No | Yes | Yes | Yes | **Yes** |

**Position:** *the file manager you would show someone.* Beginner-legible surface, prosumer
depth one layer down, and a material nobody else on Android has.

## 5. What we are explicitly not competing on

* Root browsing and system partition editing.
* Network protocols (FTP/SMB/WebDAV) at launch.
* Cloud storage integration at launch.
* Being the smallest APK.

## 6. Business context

* Free, no ads, no account, no telemetry beyond opt-in crash reporting.
* Distribution: Google Play primary, GitHub releases secondary (F-Droid-compatible build
  flavour without crash reporting is a V1 goal).
* The All Files Access declaration means Play review is a hard gate on every release. Build
  the store listing copy early, not the week before launch. See
  `architecture/PERMISSIONS.md` §7.
