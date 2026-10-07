# USB OTG checklist (ALL_IN_ONE_PLAN.md 3.5)

What the code now does, and what still needs a phone and a USB drive to confirm. None of the device rows below have been run yet; record results here with the device, Android version, drive and filesystem.

## In the code (7 October 2026)

| Behaviour | Where | Covered by |
|---|---|---|
| A drive plugged in or pulled out refreshes the volume list at once (media mount/unmount/remove/eject broadcasts), not on the next resume | `MainActivity` mount watcher | Device only |
| A drive pulled out mid-operation reports "The storage was removed or can't be reached" (EIO, ENODEV, ENXIO, also when Android wraps the errno inside an `IOException`) instead of a generic I/O error; the journal records that error | `ExceptionMapping`, `BrowseViewModel` | `ExceptionMappingTest` |
| New folder and Rename on a USB drive or SD card refuse names FAT/exFAT can't store: `" * : < > ? \ \|`, control characters, a trailing dot or space, more than 255 UTF-16 units | `RemovableNames`, `FileActionDialogs` | `RemovableNamesTest`, `FileActionDialogsTest` |

## To run on a device

| # | Step | Expected |
|---|---|---|
| 1 | Plug in a FAT32 drive with the app on Storage | The drive appears within 2 s |
| 2 | Unplug it | It disappears within 2 s; no crash |
| 3 | Start copying a large file to the drive, pull it out halfway | The operation fails with the "removed" message; Operations shows it failed; no half file is left under the real name on the next plug-in (only a `.atomic-part` or nothing) |
| 4 | On the drive, try New folder `10:30` | The sheet says the drive can't store ":" |
| 5 | Repeat 1–4 with exFAT | Same results |
| 6 | Copy a file named `report.` from internal storage to the drive | Note what happens (the engine doesn't rename on copy yet) |
