# Drag-and-Drop System

Refract implements a direct-manipulation drag-and-drop system built using Jetpack Compose's pointer gestures and drag architecture (`Modifier.dragAndDropSource` / `Modifier.dragAndDropTarget` and `detectDragGesturesAfterLongPress`), specifically engineered for high-performance multi-selection file management across storage backends.

## 1. Core Architecture & Separation of Concerns

Rather than scattering drag state into individual list rows or grid items, Refract uses an explicit, centralized controller architecture:

- **`FileDragController`**: Central coordinator holding the active `DragSession` state (`isDragging`, `dragPosition`, `activeTarget`, `itemsBeingDragged`, `originLocation`).
- **`DragSession`**: Represents the current drag interaction lifecycle, containing a unique session ID, start offset, and payload.
- **`DragPayload`**: Contains:
  - `sessionId: String`
  - `items: List<FileNodeId>`
  - `originLocation: FileNodeId`
  - `selectionCount: Int`
  - `totalBytes: Long`
- **`ActiveDropTarget`**: Represents the current target under the pointer, including:
  - `destinationId: FileNodeId`
  - `type: DropTargetType` (`FOLDER`, `TRANSFER_BUBBLE`, `STORAGE_LOCATION`, `PANEL`)
  - `isWritable: Boolean`

## 2. Interaction & Pickup UX

### Single-File Drag
- User long-presses an item and begins moving beyond the drag touch slop.
- Visual feedback:
  - Haptic feedback tick on pickup (`HapticFeedbackType.LongPress`).
  - Item lifts visually: animated scale up to `1.03f`, elevated shadow `8.dp`.

### Multi-Selection Drag
- When multiple items are selected in selection mode and the user initiates a drag on any selected item:
  - All selected items are aggregated into the `DragPayload`.
  - Floating drag preview renders a stacked card thumbnail (up to 3 previews maximum) overlaid with a high-contrast badge count (e.g. `[Cards] 12`).
  - Never renders dozens of individual thumbnails in memory during drag.

## 3. Valid Drop Destinations & Target Feedback

Valid drop targets include:
1. **Folder items in list or grid**
2. **Breadcrumb segments in the path bar**
3. **Transfer Bubbles in the bubble rail**
4. **The secondary browse pane in dual-pane mode**

When a drag enters a valid drop target:
- Elevation and border highlight are applied to the target (`Modifier.fileDropTarget`).
- Shows destination highlight and "Drop here" badge.
- A light haptic confirmation fires once upon target entry.

## 4. Desktop-Style Spring-Loaded Folder Navigation

When the user hovers over a folder while dragging files:
- Dwell detection is active for **500–750ms**.
- A circular progress animation appears over the target folder.
- When the delay completes, the folder opens immediately without dropping the files.
- The drag session remains active, allowing users to traverse nested hierarchies (`Downloads` $\to$ `Work` $\to$ `Refract`) without releasing their grip.
- Hovering over breadcrumb items allows moving back upward through the folder tree.

## 5. Edge Auto-Scroll

When dragging near the top or bottom boundaries of the scrollable list:
- `Modifier.edgeAutoScroll` detects pointer distance to the viewport edge.
- Automatically scrolls the `LazyListState` with velocity proportional to proximity to the edge.
- Capped at smooth, controllable scroll speeds to prevent runaway directory traversal.

## 6. Drop Decisions & Move Safety

When a drop is committed:
- If dropped onto a **Transfer Bubble**:
  - Immediately stages references via `Add to Bubble` without moving physical bytes.
- If dropped onto a **Folder / Pane**:
  - Checks destination capabilities (`destination.capabilities.canWrite`).
  - Displays `DropDecisionDialog` with options:
    - **Move here**: Executes transactional move (source is only deleted after destination is verified).
    - **Copy here**: Copies items with conflict resolution options (`Replace`, `Keep Both`, `Skip`).
    - **Cancel**: Dismisses without making changes.
