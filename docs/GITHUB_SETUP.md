# 360 Launcher — GitHub CI/CD Setup Guide

Complete setup from zero to automated APK builds.

---

## Repo Structure

Your repository must look like this for workflows to work:

```
360-Launcher/
├── .github/
│   └── workflows/
│       ├── build-debug.yml       ← auto builds on every push to main/develop
│       └── build-release.yml     ← builds signed APK on tag push or manual run
├── app/
│   ├── src/
│   ├── build.gradle.kts          ← use the version from app-build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
    └── wrapper/
        └── gradle-wrapper.properties
```

---

## Step 1 — Generate Your Keystore (One-time)

The release APK must be signed. Do this once on your local machine.
Keep the keystore file safe — losing it means you can't update the app.

```bash
keytool -genkey -v \
  -keystore 360-launcher-release.jks \
  -alias 360launcher \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You'll be asked for:
- **Keystore password** → make it strong, you'll need it again
- **Key password** → can be same as keystore password
- **Your name, organization, city, country** → fill anything for personal use

This creates `360-launcher-release.jks`. **Do NOT commit this file to GitHub.**

Add to `.gitignore`:
```
*.jks
*.keystore
release-keystore.jks
```

---

## Step 2 — Base64 Encode the Keystore

GitHub Secrets can only store text. Encode the keystore to base64:

```bash
# macOS / Linux
base64 -i 360-launcher-release.jks | tr -d '\n' | pbcopy
# Output is now in your clipboard

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("360-launcher-release.jks")) | Set-Clipboard
```

---

## Step 3 — Add GitHub Secrets

Go to your repo on GitHub:
`Settings → Secrets and variables → Actions → New repository secret`

Add these 4 secrets exactly:

| Secret Name | Value |
|---|---|
| `KEYSTORE_BASE64` | The base64 string from Step 2 |
| `SIGNING_STORE_PASSWORD` | Keystore password you chose |
| `SIGNING_KEY_ALIAS` | `360launcher` (or whatever alias you used) |
| `SIGNING_KEY_PASSWORD` | Key password you chose |

---

## Step 4 — Push to GitHub

```bash
# Initial setup
git init
git remote add origin https://github.com/YOUR_USERNAME/360-launcher.git
git add .
git commit -m "feat: initial 360 Launcher implementation"
git push -u origin main
```

The debug workflow fires automatically on push to `main`.
Check the Actions tab in your GitHub repo to see it running.

---

## How to Trigger Each Build

### Debug APK (every push)
- Happens automatically on every push to `main` or `develop`
- Also: Actions tab → "Build Debug APK" → "Run workflow"
- Download from: Actions → select run → Artifacts section

### Release APK (signed, by tag)

```bash
# Bump version in app/build.gradle.kts (or let -PversionName handle it)
git tag v1.0.0
git push origin v1.0.0
```

This triggers `build-release.yml`, builds a signed APK, and creates a GitHub Release.

### Release APK (manual)
- Actions tab → "Build Release APK" → "Run workflow"
- Enter version name (e.g. `1.0.0`)
- Check "Create GitHub Release?" if you want a release created

---

## Step 5 — Install on Poco F6

### Option A: ADB (fastest)

```bash
# Enable Developer Options on Poco F6:
# Settings → About phone → tap MIUI version 7 times
# Settings → Additional settings → Developer options → USB debugging → ON

# Install
adb install -r path/to/360-Launcher-v1.0.0.apk

# After install, set as default launcher:
# Settings → Home screen → Default launcher → 360 Launcher
```

### Option B: Direct APK install
1. Download APK to phone (via GitHub Releases page, download link, etc.)
2. Settings → Privacy → Install unknown apps → allow your browser/file manager
3. Tap the APK file → Install
4. Settings → Home screen → Default launcher → 360 Launcher

### HyperOS-Specific: Allow it to run always
After setting as default launcher:
1. Settings → Battery & performance → App battery saver
2. Find "360 Launcher" → Set to "No restrictions"

This prevents HyperOS from killing your home screen.

---

## Expected Build Times

| Build | First run (cold) | Subsequent (cached) |
|-------|-----------------|-------------------|
| Debug | ~12–18 min | ~4–6 min |
| Release | ~15–20 min | ~5–8 min |

KSP (for Hilt + Room) and AIDL compilation are the slow parts. Caching helps a lot after the first build.

---

## Workflow File Locations

```
.github/workflows/build-debug.yml     ← copy from output files
.github/workflows/build-release.yml   ← copy from output files
app/build.gradle.kts                  ← replace with app-build.gradle.kts content
```

---

## Troubleshooting

### "Keystore file not found"
→ Check that `KEYSTORE_BASE64` secret is set and contains the full base64 string with no newlines.

### "AIDL file not found" / build fails on ILauncherOverlay
→ Verify the AIDL files are at:
```
app/src/main/aidl/com/android/launcher3/ILauncherOverlay.aidl
app/src/main/aidl/com/android/launcher3/ILauncherOverlayCallback.aidl
```
The directory path must match the AIDL package exactly.

### "KSP / Hilt annotation processing failed"
→ Make sure `id("com.google.devtools.ksp")` is in `build.gradle.kts` plugins
→ Check that `ksp(...)` dependencies use the right KSP version (must match Kotlin version)

### Build succeeds but APK not found
→ Check artifact path in workflow: `app/build/outputs/apk/debug/app-debug.apk`
→ The filename might differ if you changed applicationId

### "softprops/action-gh-release not found"
→ Add to your repo's allowed actions:
  Settings → Actions → General → Allow all actions (or add `softprops/action-gh-release` to allowlist)
