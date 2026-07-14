# 360 Launcher — Complete Handover Package
**Project:** Custom Android Launcher for Poco F6 (HyperOS / Android 14)**  
**Distribution:** Personal sideload  
**Stack:** Kotlin · Jetpack Compose · Hilt · Room · DataStore · Coil  
**Package:** com.launcher360v2

---

## What's in This Package

| File | What it is | Where it goes |
|------|-----------|---------------|
| `01-BUILD-AND-MANIFEST.md` | Gradle build files, AndroidManifest, AIDL | Extract code into project |
| `02-DATA-LAYER.md` | Room DB, models, DataStore, repositories | `app/src/main/java/.../data/` |
| `03-DOMAIN-LAYER.md` | FeedBridge, IconCache, IconPackManager, FocusMode, PredictiveEngine | `app/src/main/java/.../domain/` |
| `04-UI-LAYER.md` | LauncherActivity, HomeScreen, AppDrawer, AppIcon, Theme | `app/src/main/java/.../ui/` |
| `05-DI-WIDGETS-SETTINGS-PROMPT.md` | Hilt DI, Widget host, Settings screens + **AI build prompt** | See below |
| `app-build-UPDATED.gradle.kts` | Final `app/build.gradle.kts` with signing config | Replace `app/build.gradle.kts` |
| `build-debug.yml` | GitHub Actions — auto builds debug APK on every push | `.github/workflows/build-debug.yml` |
| `build-release.yml` | GitHub Actions — signed release APK on tag push | `.github/workflows/build-release.yml` |
| `GITHUB_SETUP.md` | Keystore generation, GitHub Secrets setup, ADB install guide | Read before first push |
| `360-Launcher-Demo.jsx` | Interactive browser demo (React) — all features simulated | Run in any React env or Claude |

---

## How to Hand Over to an AI Builder

1. Open `05-DI-WIDGETS-SETTINGS-PROMPT.md`
2. Scroll to the bottom — copy the entire **MASTER PROMPT FOR AI CODE GENERATOR** block
3. Paste it as the **system prompt** in your AI coding tool (Claude Code / Cursor / Windsurf)
4. Attach all 5 spec files (`01` through `05`) as context
5. The AI will create the full Android Studio project

---

## Project Structure the AI Will Create

```
360Launcher/
├── .github/
│   └── workflows/
│       ├── build-debug.yml
│       └── build-release.yml
├── app/
│   ├── src/main/
│   │   ├── aidl/com/android/launcher3/
│   │   │   ├── ILauncherOverlay.aidl
│   │   │   └── ILauncherOverlayCallback.aidl
│   │   ├── java/com/launcher360v2/
│   │   │   ├── Launcher360App.kt
│   │   │   ├── data/
│   │   │   │   ├── model/         AppItem, HomeCell, FolderEntity, FocusSchedule
│   │   │   │   ├── db/            LauncherDatabase, HomeCellDao, FolderDao
│   │   │   │   ├── AppRepository.kt
│   │   │   │   ├── HomeRepository.kt
│   │   │   │   └── PrefsRepository.kt
│   │   │   ├── domain/
│   │   │   │   ├── FeedBridge.kt          ← Google Discover AIDL binding
│   │   │   │   ├── IconCache.kt
│   │   │   │   ├── IconPackManager.kt     ← Arcticons, Lawnicons, Delta support
│   │   │   │   ├── FocusModeManager.kt
│   │   │   │   ├── PredictiveAppsEngine.kt
│   │   │   │   └── GestureController.kt
│   │   │   ├── di/
│   │   │   │   ├── AppModule.kt
│   │   │   │   └── DatabaseModule.kt
│   │   │   ├── ui/
│   │   │   │   ├── LauncherActivity.kt
│   │   │   │   ├── LauncherRoot.kt
│   │   │   │   ├── home/          HomeScreen, HomeViewModel, WorkspacePage
│   │   │   │   ├── drawer/        AppDrawer, DrawerViewModel
│   │   │   │   ├── folder/        FolderSheet
│   │   │   │   ├── settings/      HiddenApps, FocusMode, IconPack screens
│   │   │   │   ├── common/        AppIcon, GlassSurface
│   │   │   │   └── theme/         Theme, Type, Color
│   │   │   ├── widget/            WidgetHostManager, WidgetView
│   │   │   ├── service/           NotificationBadgeService
│   │   │   └── receiver/          BootReceiver, WidgetHostReceiver
│   │   ├── res/
│   │   │   └── values/            strings.xml, styles.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts           ← use app-build-UPDATED.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
└── settings.gradle.kts
```

---

## V1 Features Included

| Feature | Status | Notes |
|---------|--------|-------|
| Home screen workspace (pager) | ✅ Complete | Multi-page, persistent via Room |
| App drawer (glass, bottom sheet) | ✅ Complete | Spring animation, 120Hz tuned |
| App hiding | ✅ Complete | Drawer-only filter, home screen unaffected |
| Google Discover feed (← swipe) | ✅ Complete | ILauncherOverlay AIDL, Smart Page fallback |
| Full gesture navigation | ✅ Complete | systemGestureExclusionRects handled |
| Icon pack support | ✅ Complete | ADW/Nova standard — Arcticons, Lawnicons, etc. |
| Android widget support | ✅ Complete | AppWidgetHost wrapped in AndroidView |
| Focus mode | ✅ Complete | Manual + timed schedule, user-defined distraction list |
| Predictive app row | ✅ Complete | UsageStatsManager, time-of-day scoring |
| Smart clock (wallpaper-adaptive) | ✅ Complete | WallpaperColors contrast check |
| Notification badge counts | ✅ Complete | NotificationListenerService |
| Folder support | ✅ Complete | Room-backed, drag-to-create |
| HyperOS battery exemption flow | ✅ Complete | First-launch dialog, guided setup |
| GitHub Actions CI/CD | ✅ Complete | Debug (auto) + Release (tagged) |

---

## First 3 Steps After Receiving This

1. **Run the `keytool` command** in `GITHUB_SETUP.md` to generate your signing keystore
2. **Add 4 GitHub Secrets** (`KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`)
3. **Push to GitHub** — first debug APK builds automatically in ~15 minutes

---

## Critical HyperOS Notes for the AI Builder

- The launcher window MUST be transparent (`windowShowWallpaper=true`, `windowIsTranslucent=true`)
- Battery optimization exemption dialog MUST show on first launch or HyperOS kills the home screen
- AIDL files MUST be at `src/main/aidl/com/android/launcher3/` — path is the package
- App hiding filters ONLY in `DrawerViewModel` — `HomeViewModel` never filters by hidden list
- All icon loading is async on `Dispatchers.Default` — never on the main thread
- Use `spring()` animations everywhere — `tween()` looks mechanical at 120Hz
