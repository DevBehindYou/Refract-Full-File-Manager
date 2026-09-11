# Instagram-Style Quick Peek

Refract provides an instant media inspection experience modeled after press-and-hold previews in modern photo and social applications, allowing users to inspect images and videos quickly without leaving the folder browser.

## 1. The Gesture Conflict Problem & `GestureArbiter`

In a rich file manager, a file item must support:
- **Tap**: Open the file in the full viewer.
- **Press & Hold**: Inspect media (Quick Peek).
- **Drag & Drop**: Pick up the file to move or copy it.
- **Long Press on Body / Filename**: Selection or context menu.

If both Quick Peek and file dragging claim the same unrestricted long-press gesture, race conditions or accidental drags occur. Refract solves this with `GestureArbiter` (`mediaGestureArbiter`):

```kotlin
@Composable
fun Modifier.mediaGestureArbiter(
    node: FileNode,
    peekDelayMs: Long = 350L,
    onTap: () -> Unit,
    onQuickPeek: (FileNode) -> Unit,
    onDismissPeek: () -> Unit,
): Modifier
```

### Event Resolution:
1. **Tap (< 350ms)**: Finger is lifted before the hold delay. Fires `onTap()` to open the normal viewer.
2. **Stationary Hold (>= 350ms)**: User keeps finger down without moving. A light haptic pulse fires (`HapticFeedbackType.LongPress`), and `onQuickPeek(node)` activates the preview overlay.
3. **Release**: As soon as the pointer is released, `onDismissPeek()` dismisses the overlay with a smooth fade.
4. **Hold + Movement**: When selection mode is active or when dragging is triggered outside media previews, the gesture transitions into the `FileDragController` pickup flow.

## 2. Image Quick Peek UX

- **Surface**: Centered Material 3 card using tonal elevation on phones (occupying ~70–90% of screen width) and anchored near the item on tablets.
- **Background**: Standard Material scrim (`Color.Black.copy(alpha = 0.5f)`). Zero glassmorphism, blur shaders, or AGSL runtime effects.
- **Metadata**: Renders thumbnail bitmap, file name, image dimensions (width $\times$ height), and formatted file size.
- **Asynchronous Loading**: Bitmaps and metadata are decoded on background dispatchers via `ImagePreviewHelper` and cached; never blocking the main UI thread during gesture tracking.

## 3. Video Quick Peek UX

- **On-Demand Player Binding**: Video players are never instantiated for every visible row in the list. Media3 is bound *only* when Quick Peek becomes active.
- **Muted Playback**: Default playback starts muted to prevent unexpected audio output while browsing.
- **Immediate Resource Cleanup**: Upon pointer release or gesture cancellation, player instances and audio focus are recycled immediately.

## 4. Accessibility (a11y)

- Users relying on TalkBack, switch access, or motor accessibility tools can access Quick Peek directly via:
  - The item's 3-dot context menu $\to$ **Quick preview**.
  - Custom accessibility actions assigned on `FileListItem`.
