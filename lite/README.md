# SakuDextop Lite

SakuDextop Lite is a separate, native Android-only build. It does not compile or package the main Flutter desktop application.

## Scope

- Create and remove an Android overlay virtual display.
- Launch a selected installed app on the virtual display.
- Use the phone screen as a touchpad for the selected display: pointer movement, tap/click, long-press drag, and two-finger scroll.

Linux/container support, Plasma desktop UI, desktop launcher, keyboard tools, gamepad overlays, mirroring services, and the main Flutter engine are intentionally outside this Lite project.

## Build

From the repository root:

```sh
gradle --no-daemon -p lite :app:assembleRelease
```

The APK is written to `lite/app/build/outputs/apk/release/app-release.apk`.

The GitHub Actions workflow also publishes the APK as `saku-dextop-lite-release` and reports its measured size. The target is 20 MiB; the actual APK size must be measured from the build artifact.

## Device setup

1. Install and start Shizuku, then grant SakuDextop Lite permission.
2. Root can also be used to create/remove the virtual display. Shizuku permission is required for display-targeted pointer injection.
3. Create a virtual display, open an app on it, then use the touchpad area on the phone screen.
4. Stop the virtual display from Lite when finished. Lite removes only the overlay display specification it created and preserves other specifications.
