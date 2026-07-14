// ILauncherOverlay.aidl
package com.android.launcher3;

import com.android.launcher3.ILauncherOverlayCallback;

interface ILauncherOverlay {
    void startScroll();
    void onScroll(float progress);
    void endScroll();
    void windowAttached(in android.view.WindowManager.LayoutParams attrs,
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
