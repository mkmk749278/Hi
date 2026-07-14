package com.launcher360v2.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import com.launcher360v2.data.model.AppItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /**
     * Reactive stream of all installed, launchable apps.
     * Automatically updates when apps are installed/removed/changed.
     */
    val allApps: Flow<List<AppItem>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(p: String, u: UserHandle) = refresh()
            override fun onPackageRemoved(p: String, u: UserHandle) = refresh()
            override fun onPackageChanged(p: String, u: UserHandle) = refresh()
            override fun onPackagesAvailable(pkgs: Array<String>, u: UserHandle, r: Boolean) = refresh()
            override fun onPackagesUnavailable(pkgs: Array<String>, u: UserHandle, r: Boolean) = refresh()

            fun refresh() {
                trySend(loadAll())
            }
        }
        launcherApps.registerCallback(callback)
        send(loadAll())
        awaitClose { launcherApps.unregisterCallback(callback) }
    }.flowOn(Dispatchers.Default)

    private fun loadAll(): List<AppItem> {
        return userManager.userProfiles
            .flatMap { profile ->
                launcherApps.getActivityList(null, profile).map { info ->
                    info.toAppItem(profile)
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launchApp(item: AppItem) {
        launcherApps.startMainActivity(item.componentName, item.user, null, null)
    }

    fun openAppInfo(item: AppItem) {
        launcherApps.startAppDetailsActivity(item.componentName, item.user, null, null)
    }

    fun getActivityInfo(componentFlat: String, user: UserHandle): LauncherActivityInfo? {
        val cn = android.content.ComponentName.unflattenFromString(componentFlat) ?: return null
        return try {
            launcherApps.getActivityList(cn.packageName, user)
                .firstOrNull { it.componentName == cn }
        } catch (e: Exception) { null }
    }

    private fun LauncherActivityInfo.toAppItem(user: UserHandle) = AppItem(
        label = label?.toString() ?: componentName.className,
        packageName = componentName.packageName,
        componentName = componentName,
        user = user
    )
}
