# Permission Flow

## 1. First launch

```mermaid
flowchart TD
    A[Cold start] --> B[Splash]
    B --> C{First run?}
    C -->|no| D{Access still sufficient?}
    D -->|yes| HOME[Home]
    D -->|no| CARD[Home with an inline PermissionCard]
    C -->|yes| ON[Onboarding: 2 screens]
    ON --> SETUP[Permission setup]
    SETUP --> API{SDK_INT}

    API -->|"27–28"| P1[Request READ + WRITE_EXTERNAL_STORAGE]
    P1 -->|granted| HOME
    P1 -->|denied| LIM[Limited mode + card]

    API -->|"29"| P2[Request READ_EXTERNAL_STORAGE]
    P2 --> T2["Offer: choose a folder to manage<br/>(ACTION_OPEN_DOCUMENT_TREE)"]
    T2 --> HOME

    API -->|"30–32"| P3{User choice}
    API -->|"33+"| P3
    P3 -->|All files access| MES[ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION]
    MES --> RES[onResume: isExternalStorageManager?]
    RES -->|true| HOME
    RES -->|false| P3
    P3 -->|Choose a folder| TREE[ACTION_OPEN_DOCUMENT_TREE]
    TREE --> PERSIST[takePersistableUriPermission] --> HOME
    P3 -->|Not now| MEDIA["Request READ_MEDIA_* (33+)<br/>or READ_EXTERNAL_STORAGE"] --> HOME
```

## 2. Access level resolution

```mermaid
flowchart TD
    S[refresh] --> A{isExternalStorageManager?}
    A -->|true| ALL[ALL_FILES]
    A -->|false| B{Any persisted tree grants?}
    B -->|yes| TREE[TREE_GRANTS]
    B -->|no| C{Media permissions?}
    C -->|full| MED[MEDIA_ONLY]
    C -->|"partial (API 34+)"| PART[PARTIAL_MEDIA]
    C -->|none| NONE[NONE — app-private files only]
```

Every level renders a working app. `NONE` shows app-private files, Settings, and a route to
more access — never an error screen.

## 3. Notification permission, requested lazily

```mermaid
sequenceDiagram
    actor U as User
    participant UI
    participant SAM as StorageAccessManager
    participant OS as Android

    U->>UI: start a copy of 2,000 files
    UI->>SAM: needsNotificationPermission()?
    SAM-->>UI: yes (API 33+, not granted)
    UI-->>U: "Allow notifications so you can see copy progress"
    U->>UI: Allow
    UI->>OS: request POST_NOTIFICATIONS
    OS-->>UI: granted / denied
    Note over UI: either way, the operation starts —<br/>denial only removes the notification
```

## 4. Revocation while running

```mermaid
flowchart TD
    A[App resumed] --> B[StorageAccessManager.refresh]
    B --> C{Level dropped?}
    C -->|no| D[Continue]
    C -->|yes| E[Cancel operations that need the lost access]
    E --> F[Update UI state]
    F --> G{User is inside an unreachable folder?}
    G -->|yes| H[Pop to Browse with an explanation]
    G -->|no| I[Show an inline PermissionCard]
```

## 5. The honest-limits rule

Whenever the user tries to reach `/Android/data` or `/Android/obb` on API 30+, the app shows:

> **Android blocks this folder**
> Since Android 11, no file manager can open app data folders. This is a system restriction,
> not a limitation of Refract.

and offers no retry button. Pretending otherwise generates support load and one-star reviews.
