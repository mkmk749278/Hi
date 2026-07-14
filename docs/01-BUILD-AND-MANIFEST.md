# 360-LAUNCHER — BUILD FILES & MANIFEST
# Hand this entire repo to an AI coder. Every file is complete and runnable.
# Target: Poco F6 / HyperOS / Android 14 — personal sideload only.

---

## FILE: settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "360Launcher"
include(":app")
```

---

## FILE: build.gradle.kts (ROOT)

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.53.1" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
```

---

## FILE: app/build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    kotlin("plugin.parcelize")
}

android {
    namespace = "com.launcher360v2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.launcher360v2"
        minSdk = 29           // Android 10 — gesture nav + WindowInsets APIs
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        aidl = true           // Required for ILauncherOverlay Google feed
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // ── Compose BOM ────────────────────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-graphics")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    // ── Core ───────────────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.15.0")

    // ── Navigation ─────────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ── Hilt DI ────────────────────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Room ───────────────────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ── DataStore ──────────────────────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // ── Image Loading ──────────────────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ── Coroutines ─────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ── Serialization ──────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ── Palette (wallpaper color extraction) ───────────────────────────────────
    implementation("androidx.palette:palette-ktx:1.0.0")

    // ── AppCompat (needed for icon pack resource loading) ──────────────────────
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ── Debug ──────────────────────────────────────────────────────────────────
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

---

## FILE: app/proguard-rules.pro

```proguard
# Keep AIDL-generated classes for Google Feed bridge
-keep class com.android.launcher3.** { *; }
-keep interface com.android.launcher3.** { *; }

# Keep Hilt
-keepclasseswithmembers class * { @dagger.hilt.* <methods>; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Keep Serializable models
-keepclassmembers class com.launcher360v2.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Coil
-dontwarn coil.**
```

---

## FILE: app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- ── Core Launcher Permissions ── -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <!-- ── Feed Integration Permissions ── -->
    <uses-permission android:name="com.android.launcher.permission.READ_SETTINGS" />
    <uses-permission android:name="com.android.launcher.permission.WRITE_SETTINGS" />
    <uses-permission android:name="com.google.android.apps.nexuslauncher.permission.HOTSEAT_DATA" />

    <!-- ── Usage Stats (Predictive Row) ── -->
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />

    <!-- ── Widget Support ── -->
    <uses-permission android:name="android.permission.BIND_APPWIDGET" />
    <uses-permission android:name="android.permission.APPWIDGET_LIST" />

    <!-- ── Notification Access (badge counts) ── -->
    <uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        tools:ignore="ProtectedPermissions" />

    <!-- ── Shortcuts ── -->
    <uses-permission android:name="com.android.launcher.permission.INSTALL_SHORTCUT" />

    xmlns:tools="http://schemas.android.com/tools"

    <application
        android:name=".Launcher360App"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.Launcher360"
        android:hardwareAccelerated="true"
        android:largeHeap="false"
        android:supportsRtl="true">

        <!-- ── Main Launcher Activity ── -->
        <activity
            android:name=".ui.LauncherActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true"
            android:clearTaskOnLaunch="true"
            android:resumeWhilePausing="true"
            android:taskAffinity=""
            android:windowSoftInputMode="adjustPan"
            android:configChanges="keyboard|keyboardHidden|mcc|mnc|navigation|orientation|screenLayout|screenSize|smallestScreenSize|uiMode">
            <intent-filter android:priority="1">
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- ── Settings Activity (accessible from home screen long-press) ── -->
        <activity
            android:name=".ui.settings.SettingsActivity"
            android:exported="false"
            android:label="Launcher Settings"
            android:parentActivityName=".ui.LauncherActivity" />

        <!-- ── Widget Host Provider ── -->
        <receiver
            android:name=".widget.WidgetHostReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
        </receiver>

        <!-- ── Notification Badge Service ── -->
        <service
            android:name=".service.NotificationBadgeService"
            android:exported="true"
            android:label="Notification Access"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <!-- ── Boot Receiver (restore after reboot) ── -->
        <receiver
            android:name=".receiver.BootReceiver"
            android:exported="true">
            <intent-filter android:priority="1000">
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>

    </application>

</manifest>
```

---

## FILE: app/src/main/res/values/styles.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Transparent window — wallpaper shows through -->
    <style name="Theme.Launcher360" parent="android:Theme.Material.NoTitleBar">
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:colorBackgroundCacheHint">@null</item>
        <item name="android:windowShowWallpaper">true</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
        <item name="android:windowFullscreen">false</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

---

## FILE: app/src/main/res/values/strings.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">360 Launcher</string>
    <string name="search_apps">Search apps…</string>
    <string name="hidden_apps">Hidden Apps</string>
    <string name="focus_mode">Focus Mode</string>
    <string name="icon_packs">Icon Packs</string>
    <string name="no_apps_found">No apps found</string>
    <string name="battery_opt_title">Allow 360 Launcher to run always</string>
    <string name="battery_opt_message">Tap OK, then select "Don\'t optimize" to prevent HyperOS from killing your home screen.</string>
</resources>
```

---

## FILE: app/src/main/aidl/com/android/launcher3/ILauncherOverlay.aidl
## (Google Discover Feed — AIDL interface. Must match exactly.)

```aidl
// ILauncherOverlay.aidl
package com.android.launcher3;

import com.android.launcher3.ILauncherOverlayCallback;

interface ILauncherOverlay {
    void startScroll();
    void onScroll(float progress);
    void endScroll();
    void windowAttached(in WindowManager.LayoutParams attrs,
                        in ILauncherOverlayCallback cb,
                        int flags);
    void windowDetached(boolean isChangingConfigurations);
    void closeOverlay(int flags);
    void onPause();
    void onResume();
    void openOverlay(int flags);
    void requestVoiceDetection(boolean start);
    String getVoiceSearchLanguage();
    boolean isVoiceDetectionRunning();
    boolean hasOverlayContent();
    void windowAttached2(in Bundle bundle, in ILauncherOverlayCallback cb);
    void unusedMethod();
    void setActivityState(int flags);
    boolean startSearch(in byte[] data, in Bundle bundle);
}
```

---

## FILE: app/src/main/aidl/com/android/launcher3/ILauncherOverlayCallback.aidl

```aidl
// ILauncherOverlayCallback.aidl
package com.android.launcher3;

interface ILauncherOverlayCallback {
    void overlayScrollChanged(float progress);
    void overlayStatusChanged(int status);
}
```
