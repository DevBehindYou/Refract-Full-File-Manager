# Future Considerations

Ideas beyond V1. Nothing here is committed. Each entry states what would have to be true before
it becomes real, because the failure mode for a roadmap document is a list of features nobody
ever pressure-tested.

## 1. Platform reach

**Wear OS companion.** Only useful as a transfer target and a storage-status glance. Would
require the module split to be complete first.

**Android TV / large-screen media.** Different input model entirely. Would need a separate
navigation layer; the file domain would carry over unchanged, which is a good sign the domain
layer is right.

**Desktop via Compose Multiplatform.** The domain layer is already pure Kotlin, so it ports.
The three storage backends do not — a desktop backend would be a fourth implementation of
`StorageBackend`. The interface was designed to make this possible, not because it is planned.

**16 KB page sizes and future ABI changes.** Already accounted for in Phase 12; a standing item
rather than a feature.

## 2. Storage and sync

**Selective cloud provider access via SAF.** Not cloud sync — just treating a cloud provider's
`DocumentsProvider` as another volume, which SAF already permits. Cheap, honest, and does not
break the local-first premise, because we never hold credentials or copy data to our servers.
This is the only cloud-adjacent idea worth considering.

**Two-device transfer over local Wi-Fi.** Attractive and dangerous. Would need a real security
review: pairing, transport encryption, and an explicit trust model. Not worth doing casually.

**Versioned trash / file history.** Storage cost grows without bound unless capped. Would need a
clear quota model before it is designed.

## 3. On-device intelligence

Any intelligent feature in Refract must satisfy **all four** of these, or it does not ship:

1. **On-device only.** No file, no file name, no path, no thumbnail leaves the device. Ever.
   The base flavour has no `INTERNET` permission and that must not change for this.
2. **Off by default.** The user turns it on deliberately, having read what it does.
3. **Useful offline.** If a feature stops working in airplane mode, it was never on-device.
4. **Deletable.** Removing the feature leaves a complete app — the same rule the glass system
   lives under.

Candidates that could satisfy those:

* **Smart categorisation** — grouping downloads by inferred type (invoices, tickets, receipts)
  using on-device text extraction. Value is real; accuracy is the question, and a wrong
  category is only a mild annoyance, which makes it a safe place to start.
* **On-device OCR search** — search text *inside* images and PDFs using ML Kit's bundled
  models. Highest genuine utility of anything on this list. Indexing cost and battery are the
  constraints; it would have to be opt-in, incremental, and charging-only.
* **Duplicate detection by perceptual hash** — only ever as a *suggestion* surface, never
  wired to a delete action, because a false positive here costs a photo.
* **Natural-language search** ("photos from last summer") — parsed on-device into the existing
  filter model. It is a query parser, not a model call, which is exactly why it is acceptable.

Explicitly rejected: anything that uploads content for analysis, any chat interface bolted onto
a file manager, any feature whose value proposition is "AI" rather than a concrete outcome, and
any auto-organisation that moves files without per-file confirmation. **A file manager that
moves files on its own judgement is a file manager that loses files.**

## 4. Interaction ideas

* **Gesture shortcuts** — swipe a row for a configurable quick action. Needs to coexist with
  selection and accessibility; probably worth it.
* **Split-screen internal transfer** — two folders side by side on a phone in landscape.
* **Shell-style command bar** for power users (`mv *.jpg Photos/`). Small audience, high risk,
  interesting.
* **Shortcut and widget deep links** to specific folders.

## 5. Ecosystem

* **Documents provider** — expose Refract's favourites and recents to other apps through a
  `DocumentsProvider`. Genuinely useful and architecturally clean.
* **Quick Share / nearby integration** as a share target.
* **Backup integration** — export favourites, settings, and bookmarks; explicitly never files.

## 6. Things we will not do, and why

| Idea | Why not |
|---|---|
| Ads or "boost" upsells | The category is full of these and they are why people distrust file managers |
| Root file access | Enormous risk surface, tiny audience, one bad write bricks a device |
| Built-in cleaner that "frees space automatically" | Automatic deletion of user data is indefensible regardless of how good the heuristic is |
| Analytics on file names, types, or paths | Even aggregated, this is the user's private index of their life |
| A cloud account system | Contradicts the entire premise of the app |
| Antivirus scanning | Cannot be done meaningfully on-device; the feature would be theatre |
| Social or sharing feeds | Not what a file manager is |

## 7. Revisit triggers

This document should be re-read when any of the following happens: a new Android release
changes the storage model again; on-device ML models get materially cheaper to run; the module
split completes; or user feedback contradicts a "we will not do this" row above with a reason
better than preference.
