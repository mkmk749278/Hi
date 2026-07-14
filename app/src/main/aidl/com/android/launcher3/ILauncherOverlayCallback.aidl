// ILauncherOverlayCallback.aidl
package com.android.launcher3;

interface ILauncherOverlayCallback {
    void overlayScrollChanged(float progress);
    void overlayStatusChanged(int status);
}
