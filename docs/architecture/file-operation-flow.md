# File Operation Flow

## 1. Operation state machine

```mermaid
stateDiagram-v2
    [*] --> Queued: enqueue (after validation)
    Queued --> Running: executor picks it up
    Running --> AwaitingInput: name collision, policy = ASK
    AwaitingInput --> Running: user chooses
    AwaitingInput --> Cancelled: user cancels
    Running --> Paused: user pauses (V1)
    Paused --> Running: resume
    Running --> Completed: all items succeeded
    Running --> PartiallyCompleted: some items failed or skipped
    Running --> Failed: fatal (disk full, volume lost, permission revoked)
    Running --> Cancelled: user cancels
    Queued --> Cancelled: user cancels
    Running --> Interrupted: process death
    Interrupted --> Queued: rebuilt from Room on service restart
    Completed --> [*]
    PartiallyCompleted --> [*]
    Failed --> [*]
    Cancelled --> [*]
```

`Interrupted` is explicit. An operation killed by the system is never reported as
completed and never silently vanishes.

## 2. Copy of a single file

```mermaid
flowchart TD
    A[Start item] --> B{Destination exists?}
    B -->|yes| C{Collision policy}
    C -->|ASK| D[Emit AwaitingInput, suspend this operation only]
    D --> C
    C -->|SKIP| SKIP[Record skipped] --> Z[Next item]
    C -->|KEEP_BOTH| E["name (1).ext"]
    C -->|OVERWRITE| F[Write to temp name]
    B -->|no| G[Open output]
    E --> G
    F --> G
    G --> H[Stream 64–256KB buffers]
    H --> I{ensureActive?}
    I -->|cancelled| J[Discard partial target] --> K[Cancelled]
    I -->|ok| L{More bytes?}
    L -->|yes| M[Report progress, throttled 4Hz] --> H
    L -->|no| N[flush + fsync]
    N --> O{bytesWritten == source size?}
    O -->|no| P[Discard partial] --> Q[Record failure: IncompleteWrite] --> Z
    O -->|yes| R[Preserve mtime]
    R --> S{Overwrite mode?}
    S -->|yes| T[Atomic replace target with temp]
    S -->|no| U[Done]
    T --> U
    U --> V{MOVE operation?}
    V -->|yes| W[Delete source] --> Z
    V -->|no| Z
```

The two guards that prevent data loss: **discard partial on any failure**, and
**never delete the source before the copy is byte-verified**.

## 3. Move decision

```mermaid
flowchart TD
    A[Move requested] --> B{Same volume AND same backend?}
    B -->|yes| C{Backend supports atomic rename/move?}
    C -->|yes| D["rename() — O(1)"]
    C -->|no| E[Copy + verify + delete]
    B -->|no| E
    D --> F[Done, undo = reverse rename]
    E --> G{Copy verified?}
    G -->|yes| H[Delete source] --> I[Done, undo = move back]
    G -->|no| J[Leave source intact, report failure]
```

## 4. Queue behaviour

```mermaid
flowchart LR
    E1[Enqueue] --> Q[(Room: operations)]
    Q --> P{Any running?}
    P -->|no| S[Start service, run]
    P -->|yes, different volume, < 2 concurrent| S
    P -->|yes, same volume| W[Wait]
    S --> D{Queue empty?}
    D -->|yes| ST[stopSelf]
    D -->|no| S
```

## 5. Undo

```mermaid
sequenceDiagram
    participant U as User
    participant UI
    participant OR as OperationRepository
    participant DB as Room
    participant EX as Executor

    EX->>DB: Completed(summary with UndoToken)
    DB-->>UI: snackbar "3 files moved" [Undo]
    U->>UI: tap Undo (within 10s)
    UI->>OR: undo(operationId)
    OR->>DB: read UndoToken
    OR->>EX: enqueue inverse operation
    EX->>EX: move each item back to its recorded parent
    EX->>DB: Completed
    UI-->>U: "Move undone"
```

Undo is itself a normal operation: queued, progressed, cancellable, and logged. It is not a
special-cased shortcut, which is why it works for 5,000 files as well as for 3.
