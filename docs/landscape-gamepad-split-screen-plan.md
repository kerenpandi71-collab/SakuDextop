# Landscape Gamepad Split-Screen Implementation Plan

Branch: `feature/landscape-gamepad-split-screen`

## Goal
In landscape gamepad mode, show the mirrored virtual-display/game surface as a centered, bounded pane and place the controls in dedicated left and right zones. Keep the existing desktop/trackpad/keyboard modes unchanged.

## Implementation order

1. **Map the current layout and resize lifecycle**
   - `MirrorService.addWindow()` owns the mirrored `SurfaceView` and its host container.
   - `setLaptopMode()` adds/removes the deck and currently relies on a vertical weighted layout.
   - `applyLaptopGeometryWhenLaidOut()` resizes the virtual display to the measured SurfaceView size.
   - Preserve these resize and restore paths; don't change orientation globally.

2. **Add a gamepad-landscape-only host layout**
   - Select it only when the active deck style is `GAMEPAD` and the device is in landscape.
   - Use a horizontal host: mirrored surface in the center, movement control zone on the left, action controls on the right.
   - Keep the existing vertical layout for portrait gamepad and laptop/keyboard modes.
   - Recalculate the SurfaceView's measured bounds before calling `resizeActiveDisplay()`.

3. **Split controller interaction without losing multi-touch**
   - Reuse the current native gamepad input injection callbacks and button/axis codes.
   - Ensure left movement and right action inputs can be held simultaneously.
   - Avoid placing a second opaque full-screen touch layer over the game surface.

4. **Lifecycle and recovery**
   - Restore the original host orientation/weights when leaving gamepad mode or switching deck style.
   - Reapply layout on orientation changes and when the deck is rebuilt.
   - Keep menu, software cursor, trackpad, and keyboard touch routing intact.

5. **Validation**
   - Inspect the complete diff and Kotlin layout parameters.
   - Run the existing Android Gradle build using the repository's existing workflow only, if available, to avoid creating duplicate Actions runs.
   - Report compile/test status separately from device-side behavior; real simultaneous touch still needs device testing.

## Guardrails
- Do not merge into `main` until reviewed.
- Do not change the default desktop resolution or other input modes as part of this work.
- Do not claim device behavior is verified without a Redmi/Android runtime test.
