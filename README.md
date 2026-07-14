# 360 Launcher

A custom Android launcher for the Poco F6 (HyperOS / Android 14), built with
Kotlin, Jetpack Compose, Hilt, Room, DataStore and Coil. Personal sideload —
not intended for the Play Store.

Package: `com.launcher360v2` · minSdk 29 · targetSdk/compileSdk 35

## Features

- Home workspace pager (multi-page, persisted via Room)
- Glass app drawer (bottom sheet) with search
- App hiding (drawer-only filter — home screen unaffected)
- Google Discover feed slide-in via `ILauncherOverlay` AIDL
- Icon pack support (Nova/ADW standard — Arcticons, Lawnicons, Delta, …)
- Focus mode (manual + timed schedule)
- Predictive app row (UsageStats-based)
- Wallpaper-adaptive smart clock
- Notification badge counts (NotificationListenerService)
- Android widget support (AppWidgetHost wrapped in `AndroidView`)
- HyperOS battery-optimization exemption flow on first launch

## Build

```bash
./gradlew assembleDebug        # debug APK → app/build/outputs/apk/debug/
```

GitHub Actions build the debug APK automatically on every push to `main`,
`develop`, or a `claude/**` branch (see `.github/workflows/build-debug.yml`).
Tagging `vX.Y.Z` produces a signed release APK + GitHub Release
(`.github/workflows/build-release.yml`) — this requires the signing secrets
described in [`docs/GITHUB_SETUP.md`](docs/GITHUB_SETUP.md).

## Project origin & notes

This project was generated from the specification package in [`docs/`](docs/)
(`README_HANDOVER.md` + the numbered spec files). A few build-blockers in the
original specs were corrected while materialising the project so it compiles
against the pinned toolchain:

- **Compose + Kotlin 2.0** now uses the `org.jetbrains.kotlin.plugin.compose`
  Gradle plugin (required by Kotlin 2.0; the old
  `composeOptions.kotlinCompilerExtensionVersion` approach was removed).
- **`AppIcon`** loads icons asynchronously through `IconCache` (obtained via a
  Hilt `@EntryPoint`) instead of relying on a never-populated `AppItem.icon`.
- **AndroidManifest** `xmlns:tools` namespace declaration was moved onto the
  root element.
- **AIDL** references `android.view.WindowManager.LayoutParams` fully-qualified.
- Added standard project plumbing the specs omitted: the Gradle wrapper,
  `gradle.properties`, `.gitignore`, and an adaptive launcher icon.

## Structure

```
app/src/main/
├── aidl/com/android/launcher3/    # Google feed AIDL
├── java/com/launcher360v2/
│   ├── data/      # models, Room db, repositories, DataStore
│   ├── domain/    # FeedBridge, IconCache, IconPackManager, Focus, Predictive
│   ├── di/        # Hilt modules
│   ├── ui/        # Compose UI (home, drawer, settings, folder, common, theme)
│   ├── widget/    # AppWidgetHost integration
│   ├── service/   # NotificationBadgeService
│   └── receiver/  # BootReceiver
└── res/           # styles, strings, launcher icon
```
