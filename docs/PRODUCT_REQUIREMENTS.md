# Product Requirements

## 1. Vision statement

Refract is a native Android file manager that is immediately legible to someone who has
never thought about a filesystem, and complete enough that a power user never installs a
second one. Its interface is built from a custom Liquid Glass material that refracts the
content beneath it, degrading gracefully to a clean Material surface on any device from
Android 8.1 upward.

## 2. Success criteria

| Metric | Target |
|---|---|
| Cold start to first interactive frame | < 800 ms (mid-tier), < 500 ms (flagship) |
| Time to locate a known recent download, first-time user | < 10 s, ≤ 3 taps |
| Crash-free sessions | ≥ 99.7% |
| Data-loss incidents | **0** — any single incident is a P0 stop-ship |
| Play policy review | Passes All Files Access declaration first submission |
| Play Store rating | ≥ 4.5 after 1,000 ratings |
| Frame rate during folder scroll (1,000 items) | ≥ 58 fps p90 on reference device |

## 3. Jobs to be done

1. *When I have just downloaded something, I want to find and open it immediately, so I do
   not have to remember where Android put it.*
2. *When my storage is full, I want to see what is using it and delete safely, so I can take
   photos again.*
3. *When I need to send a file, I want to share it in two taps, so I do not leave the app.*
4. *When I am organising, I want to move many files at once and know it completed, so I
   trust the result.*
5. *When I receive an archive, I want to look inside and extract it, without another app.*
6. *When I am looking for something by name, I want results as I type across all storage.*

Every MVP screen maps to at least one of these. A screen mapping to none is cut.

## 4. Product principles

1. **Safety over speed.** A destructive action is confirmed, batched into an undoable
   operation, or both. Delete defaults to a recoverable trash where the platform allows it.
2. **Honest about the platform.** If Android will not let us into `/Android/data`, we say so
   in plain language and offer the alternative. We never fake capability.
3. **Progressive disclosure.** Home shows six things. Everything else is one deliberate step
   away — long press, overflow, or the command palette.
4. **One primary action per surface.** Never two competing FABs, never a toolbar of peers.
5. **Glass is chrome.** It marks what floats and what is interactive. Content is opaque.
6. **Nothing blocks on I/O.** Every list paints its skeleton in one frame and fills in.
7. **Local-first.** Nothing leaves the device without explicit, specific consent.

## 5. Information architecture

Five bottom destinations, validated against the jobs above:

| Destination | Serves JTBD | Contents |
|---|---|---|
| **Home** | 1, 2, 3 | Storage meter, category grid, recents, favourites, quick actions |
| **Browse** | 4 | Volume list → folder tree, breadcrumb, view/sort controls |
| **Search** | 6 | Full-screen search with filters and scopes |
| **Storage** | 2 | Analyser, largest files/folders, duplicates (V1), cleanup |
| **More** | 5 | Archives, operations, favourites, settings, about |

### Why five and not four

The tempting cut is folding Storage into Home. It was rejected: storage analysis is the
strongest differentiator against Files by Google and it is the answer to the most common
user problem (JTBD 2). Burying it under a scroll makes it invisible.

### Why "More" and not a hamburger

A drawer conflicts with edge-to-edge back gestures and adds a navigation model users must
learn. "More" is a normal destination hosting a simple list.

## 6. Key product decisions and their rationale

| Decision | Rationale |
|---|---|
| Categories on Home, filesystem in Browse | Priya never needs a path; Dev always does. Both are first class. |
| Delete → app trash where possible, not immediate unlink | JTBD safety. Real deletion is a second, explicit action. |
| Operations run in a foreground service | A copy must survive the user leaving the app. |
| No onboarding carousel | Permission setup is the onboarding. Three screens maximum. |
| Search is a destination, not a bar on Home | It needs full-screen filters and history; a bar implies triviality. |
| Glass tier is auto but user-overridable | Respects both the device and the user who wants it off. |

## 7. Constraints

* minSdk 27, targetSdk 36. No feature may require API > 27 to be *usable*; features may
  require higher APIs to be *available*, with a documented fallback.
* No account system, no server, no network permission at MVP except optional crash reporting
  (which is a build flavour, not a runtime toggle).
* APK size target < 12 MB (no bundled native archive libraries at MVP).
* Single developer / small team velocity: MVP scope must be completable in 12 phases.
