# DFD — Permissions (Process 1.0 expanded)

```mermaid
flowchart TD
    USER([User])
    OS([Android platform])

    P11["1.1 Detect API level<br/>+ device capabilities"]
    P12["1.2 Read current grants"]
    P13["1.3 Compute AccessLevel"]
    P14["1.4 Build required steps"]
    P15["1.5 Show rationale"]
    P16["1.6 Launch system flow"]
    P17["1.7 Persist tree grants"]
    P18["1.8 Re-check on resume"]
    P19["1.9 Detect revocation"]

    D3f[("D3.f Grant records")]

    P11 --> P12
    OS -->|"checkSelfPermission,<br/>isExternalStorageManager,<br/>persistedUriPermissions"| P12
    P12 --> P13
    P13 -->|"AccessLevel + per-volume access"| USER
    P13 --> P14
    USER -->|"asks for more access"| P14
    P14 --> P15
    P15 -->|"benefit + limits + lesser option"| USER
    USER -->|proceed| P16
    P16 -->|"requestPermissions /<br/>ACTION_MANAGE_ALL_FILES_ACCESS /<br/>ACTION_OPEN_DOCUMENT_TREE"| OS
    OS -->|result| P17
    P17 -->|takePersistableUriPermission| OS
    P17 --> D3f
    P17 --> P13
    OS -->|ON_RESUME| P18 --> P12
    P18 --> P19
    P19 -->|"level dropped"| USER
    P19 -->|"cancel affected operations"| P13
```

## Step model

```kotlin
sealed interface AccessStep {
    data class RuntimePermissions(val permissions: List<String>) : AccessStep
    data object AllFilesAccess : AccessStep                       // API 30+
    data class TreeGrant(val volumeId: String) : AccessStep
    data object NotificationPermission : AccessStep               // API 33+
}
```

`requiredStepsFor(target)` returns an **ordered** list for the current API level. The UI
walks it; no screen contains its own `SDK_INT` branch.

| Target | API 27–28 | API 29 | API 30–32 | API 33+ |
|---|---|---|---|---|
| Browse primary shared | `RuntimePermissions(R,W)` | `RuntimePermissions(R)` + `TreeGrant` | `AllFilesAccess` \| `TreeGrant` | `AllFilesAccess` \| `TreeGrant` \| `RuntimePermissions(media)` |
| Browse SD card | `TreeGrant` | `TreeGrant` | `AllFilesAccess` \| `TreeGrant` | same |
| Categories only | `RuntimePermissions(R)` | `RuntimePermissions(R)` | `RuntimePermissions(R)` | `RuntimePermissions(media)` |
| Background operation | — | — | — | `NotificationPermission` |

## Rationale rules (1.5)

1. Explain the benefit, not the permission name.
2. State the limitation in the same view.
3. Offer a lesser alternative with equal visual weight.
4. Never repeat immediately after a denial.
5. After two denials, route through Settings only.

## Revocation detection (1.9)

| Signal | Meaning |
|---|---|
| `checkSelfPermission` changed on resume | Runtime permission revoked |
| `isExternalStorageManager()` false when it was true | All Files Access revoked |
| `persistedUriPermissions` missing an entry | SAF grant lost |
| `SecurityException` from a backend | Grant lost since the last check — refresh immediately |

Response is always the same shape: update state, cancel affected operations, tell the user
what changed and how to fix it, never crash.
