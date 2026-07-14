package com.launcher360v2.domain

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import com.launcher360v2.data.model.IconPackInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads icon packs that follow the ADW/Nova/Lawnchair standard.
 * Supported packs: Arcticons, Lawnicons, Delta, Whicons, and any pack
 * that publishes com.novalauncher.THEME or org.adw.launcher.THEMES.
 *
 * How it works:
 *   1. Discover packs by querying intent filters
 *   2. Load appfilter.xml from the pack's resources
 *   3. Parse ComponentName → drawable-name mapping
 *   4. On getIcon(), look up mapping and load drawable from pack's Resources
 */
@Singleton
class IconPackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "IconPackManager"
        // Known intent filter actions for icon packs
        private val ICON_PACK_ACTIONS = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEMES",
            "com.dlto.atom.launcher.THEME",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME"
        )
    }

    // Currently active icon pack package name (empty = system icons)
    private var activePackage: String = ""
    private var packResources: Resources? = null
    private var packPackage: String = ""

    // Map: ComponentName.flattenToString() → drawable resource name
    private val componentMap = HashMap<String, String>()

    /** Package name of the icon pack currently loaded (empty = system icons). */
    val active: String get() = activePackage

    /**
     * Returns all installed icon packs on the device.
     */
    suspend fun getInstalledIconPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packs = mutableListOf<IconPackInfo>()

        for (action in ICON_PACK_ACTIONS) {
            val intent = Intent(action)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                if (packs.none { it.packageName == pkg }) {
                    packs.add(
                        IconPackInfo(
                            packageName = pkg,
                            label = ri.loadLabel(pm).toString(),
                            previewIcon = ri.loadIcon(pm)
                        )
                    )
                }
            }
        }
        packs
    }

    /**
     * Load and activate an icon pack. Call when user selects a pack in settings.
     * Pass empty string to reset to system icons.
     */
    suspend fun loadPack(packageName: String) = withContext(Dispatchers.IO) {
        componentMap.clear()
        packResources = null
        activePackage = packageName

        if (packageName.isEmpty()) return@withContext

        try {
            val pm = context.packageManager
            packResources = pm.getResourcesForApplication(packageName)
            packPackage = packageName
            parseAppFilter()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon pack $packageName: ${e.message}")
            activePackage = ""
        }
    }

    /**
     * Get icon for a component from the active icon pack.
     * Returns null if no pack is loaded or component has no match.
     */
    fun getIcon(componentName: ComponentName): Drawable? {
        if (activePackage.isEmpty() || packResources == null) return null

        val drawableName = componentMap[componentName.flattenToString()]
            ?: componentMap["ComponentInfo{${componentName.packageName}/}"]  // package-level fallback
            ?: return null

        return try {
            val resId = packResources!!.getIdentifier(drawableName, "drawable", packPackage)
            if (resId == 0) return null
            packResources!!.getDrawable(resId, null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load drawable $drawableName: ${e.message}")
            null
        }
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private fun parseAppFilter() {
        val res = packResources ?: return
        val filterId = res.getIdentifier("appfilter", "xml", packPackage)
        if (filterId == 0) {
            Log.w(TAG, "appfilter.xml not found in $packPackage")
            return
        }

        try {
            val parser = res.getXml(filterId)
            var event = parser.eventType

            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")

                    if (component != null && drawable != null) {
                        // component format: "ComponentInfo{pkg/cls}"
                        val clean = component
                            .removePrefix("ComponentInfo{")
                            .removeSuffix("}")
                        // Store as "pkg/cls" (flattenToString format)
                        val flat = if ("/" !in clean) "$clean/" else clean
                        componentMap[flat] = drawable
                    }
                }
                event = parser.next()
            }
            Log.d(TAG, "Loaded ${componentMap.size} icon mappings from $packPackage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse appfilter.xml: ${e.message}")
        }
    }
}
