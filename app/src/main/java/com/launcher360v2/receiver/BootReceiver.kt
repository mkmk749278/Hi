package com.launcher360v2.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED. As a HOME app the launcher is auto-started by the
 * system, so there is nothing to do here — this exists only so HyperOS keeps the
 * app in its "may run at boot" list, which helps avoid aggressive process kills.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("BootReceiver", "Boot completed: ${intent?.action}")
        // No-op: the launcher launches on demand via the HOME intent filter.
    }
}
