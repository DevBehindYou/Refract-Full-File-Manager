# Multi-Mode File Hiding Architecture

Refract eliminates the confusion of a single ambiguous "Hide" button by introducing three explicitly differentiated hiding mechanisms tailored for distinct privacy needs, transparent technical tradeoffs, and crash recovery guarantees.

---

## 1. Transparency & Security Terminology

Refract maintains strict truth in advertising:
- `.nomedia` and header obfuscation are **not** encryption.
- The UI explicitly states the privacy level, tradeoffs, and failure modes before any hiding operation is performed.

---

## 2. Mode A — Hide from Gallery (`.nomedia`)

### Purpose
Prevents selected media from appearing in photo galleries and media scanning applications.

### Architecture & Rules
- **Rule**: `.nomedia` is a filesystem marker respected by `android.provider.MediaStore`. It affects the containing directory and all descendants. Refract **never** drops a `.nomedia` file blindly into a general user folder containing unrelated photos.
- **Implementation**:
  - For individual media files, Refract manages a hidden vault folder per storage volume: `<storage>/.RefractHidden/` with a `.nomedia` file.
  - Selected files are moved safely into this directory using transactional move semantics.
  - Metadata is recorded in `HiddenFilesDatabaseHelper` tracking `originalLocation`, `currentLocation`, `displayName`, and `hiddenTimestamp`.
- **Restoration**: Unhiding moves the file back to its original location and triggers MediaStore scanning so galleries discover it again.

---

## 3. Mode B — Fast Obscure (`RefractHiddenFormat v1`)

### Purpose
Makes files unrecognizable to casual apps and file indexers instantly (O(1)), without processing or encrypting multi-gigabyte file contents.

### Technical Specification: `RefractHiddenFormat v1`
1. **Header Transformation**:
   - Reads the first up to 512 signature bytes (`HEADER_REGION_SIZE = 512`).
   - Applies an invertible XOR transformation (`XOR_KEY = 0x5A`) in-place, scrambling the MIME magic numbers (e.g., MP4/PNG/PDF signatures).
2. **Self-Contained Recovery Footer**:
   - Appends recovery metadata to the end of the file:
     - Scrambled header bytes
     - Original UTF-8 filename bytes and length
     - CRC32 checksum of the original header
     - Original file size (Long)
     - Magic footer marker: `"REFRACT_OBSCURE_V1"`
   - Flushes and syncs to disk (`raf.fd.sync()`).
3. **Safe Rename**: Renames file with `.refract_obscured` extension.
4. **Self-Contained Guarantee**: Because recovery metadata is stored directly in the file footer, files can still be recovered even if the app's SQLite database is wiped or lost.

### Transaction Journal & Crash Recovery
- Operations are journaled in `hide_journal` with states:
  - `PREPARING` $\to$ `HEADER_TRANSFORMED` $\to$ `RENAMED` $\to$ `COMMITTED`
- On application startup, `HiddenFilesRepository.recoverUnfinishedOperations()` scans for incomplete entries:
  - If a file is in `HEADER_TRANSFORMED` or `ROLLBACK_REQUIRED`, it is safely rolled back to its original state.
  - Unit tests verify byte-for-byte identical SHA-256 hashes before obfuscation and after restoration.

---

## 4. Mode C — Move to Refract Private Storage

### Purpose
Provides strong app-level privacy by moving files into Refract's private internal storage (`context.filesDir / refract_private_storage`). Other ordinary third-party applications without root cannot read or browse these files.

### Critical User Warning
- The UI explicitly informs the user:
  > *"Files stored in Refract private storage are protected from other apps, but will be deleted if Refract is uninstalled."*

### Execution Safety
- Uses transactional copy $\to$ verify byte count $\to$ delete original source semantics.
- Source file is never deleted unless the private destination copy has been verified and committed.
- Directory access can be protected by Android biometric / device credential authentication.

---

## 5. Hidden Files Management Screen (`HiddenFilesScreen`)

- Accessible from the Browse menu (`Hidden files`).
- Features a 3-tab Material 3 interface:
  - **Fast Obscured**
  - **Hidden Gallery**
  - **Private Storage**
- Displays original filename, original path, hidden timestamp, and file size.
- Actions:
  - **Batch Unhide All** (in top bar)
  - **Restore to original location**
  - **Restore to custom destination...**
  - **Permanent Delete**
