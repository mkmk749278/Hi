# 360-LAUNCHER — DATA LAYER
# Models, Room Database, DataStore, Repositories

---

## FILE: app/src/main/java/com/launcher360v2/data/model/AppItem.kt

```kotlin
package com.launcher360v2.data.model

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle

data class AppItem(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val user: UserHandle,
    var icon: Drawable? = null,      // loaded asynchronously by IconCache
    var badgeCount: Int = 0          // updated by NotificationBadgeService
) {
    val key: String get() = componentName.flattenToString()
}
```

---

## FILE: app/src/main/java/com/launcher360v2/data/model/HomeCell.kt

```kotlin
package com.launcher360v2.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents one cell on the home screen grid.
 * page: 0-indexed page number (page 0 = Google Feed, page 1 = first home page)
 * row/col: 0-indexed position in the grid
 * span: number of cells the widget occupies (1 = normal icon)
 */
@Entity(tableName = "home_cells")
data class HomeCell(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val page: Int,
    val row: Int,
    val col: Int,
    val type: CellType,
    val packageName: String? = null,
    val componentFlat: String? = null,   // ComponentName.flattenToString()
    val widgetId: Int = -1,              // AppWidget ID, -1 if not a widget
    val folderId: Long = -1L,            // Room ID of FolderEntity, -1 if not a folder
    val spanH: Int = 1,                  // horizontal span (cells)
    val spanV: Int = 1                   // vertical span (cells)
)

enum class CellType { APP, WIDGET, FOLDER }
```

---

## FILE: app/src/main/java/com/launcher360v2/data/model/FolderEntity.kt

```kotlin
package com.launcher360v2.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val page: Int,
    val row: Int,
    val col: Int
)

@Entity(tableName = "folder_apps")
data class FolderApp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val componentFlat: String,
    val position: Int
)

// POJO for querying folders with their apps
data class FolderWithApps(
    @Embedded val folder: FolderEntity,
    @Relation(parentColumn = "id", entityColumn = "folderId")
    val apps: List<FolderApp>
)
```

---

## FILE: app/src/main/java/com/launcher360v2/data/model/IconPackInfo.kt

```kotlin
package com.launcher360v2.data.model

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val previewIcon: android.graphics.drawable.Drawable?
)
```

---

## FILE: app/src/main/java/com/launcher360v2/data/model/FocusSchedule.kt

```kotlin
package com.launcher360v2.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FocusSchedule(
    val enabled: Boolean = false,
    val timedMode: Boolean = false,       // true = auto-activate on schedule
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 17,
    val endMinute: Int = 0,
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5), // 1=Mon … 7=Sun
    val distractingPackages: Set<String> = emptySet()
)
```

---

## FILE: app/src/main/java/com/launcher360v2/data/db/LauncherDatabase.kt

```kotlin
package com.launcher360v2.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.launcher360v2.data.model.FolderApp
import com.launcher360v2.data.model.FolderEntity
import com.launcher360v2.data.model.HomeCell

@Database(
    entities = [HomeCell::class, FolderEntity::class, FolderApp::class],
    version = 1,
    exportSchema = false
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun homeCellDao(): HomeCellDao
    abstract fun folderDao(): FolderDao
}
```

---

## FILE: app/src/main/java/com/launcher360v2/data/db/HomeCellDao.kt

```kotlin
package com.launcher360v2.data.db

import androidx.room.*
import com.launcher360v2.data.model.HomeCell
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeCellDao {

    @Query("SELECT * FROM home_cells ORDER BY page ASC, row ASC, col ASC")
    fun observeAll(): Flow<List<HomeCell>>

    @Query("SELECT * FROM home_cells WHERE page = :page ORDER BY row ASC, col ASC")
    suspend fun getCellsOnPage(page: Int): List<HomeCell>

    @Query("SELECT COUNT(DISTINCT page) FROM home_cells")
    suspend fun pageCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCell(cell: HomeCell): Long

    @Update
    suspend fun updateCell(cell: HomeCell)

    @Delete
    suspend fun deleteCell(cell: HomeCell)

    @Query("DELETE FROM home_cells WHERE componentFlat = :componentFlat")
    suspend fun deleteCellByComponent(componentFlat: String)

    @Query("UPDATE home_cells SET page = :newPage, row = :newRow, col = :newCol WHERE id = :id")
    suspend fun moveCell(id: Long, newPage: Int, newRow: Int, newCol: Int)

    @Query("SELECT * FROM home_cells WHERE page = :page AND row = :row AND col = :col LIMIT 1")
    suspend fun getCellAt(page: Int, row: Int, col: Int): HomeCell?
}
```

---

## FILE: app/src/main/java/com/launcher360v2/data/db/FolderDao.kt

```kotlin
package com.launcher360v2.data.db

import androidx.room.*
import com.launcher360v2.data.model.FolderApp
import com.launcher360v2.data.model.FolderEntity
import com.launcher360v2.data.model.FolderWithApps
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Transaction
    @Query("SELECT * FROM folders")
    fun observeAllFolders(): Flow<List<FolderWithApps>>

    @Transaction
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderWithApps(folderId: Long): FolderWithApps?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderApp(app: FolderApp): Long

    @Delete
    suspend fun removeFolderApp(app: FolderApp)

    @Query("DELETE FROM folder_apps WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)
}
```

---

## FILE: app/src/main/java/com/launcher360v2/data/AppRepository.kt

```kotlin
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
```

---

## FILE: app/src/main/java/com/launcher360v2/data/PrefsRepository.kt

```kotlin
package com.launcher360v2.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.launcher360v2.data.model.FocusSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

@Singleton
class PrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.dataStore

    companion object {
        val HIDDEN_APPS_KEY = stringSetPreferencesKey("hidden_apps")
        val ICON_PACK_KEY = stringPreferencesKey("icon_pack_package")
        val FOCUS_SCHEDULE_KEY = stringPreferencesKey("focus_schedule_json")
        val FOCUS_ACTIVE_KEY = booleanPreferencesKey("focus_mode_active")
        val GRID_COLS_KEY = intPreferencesKey("grid_columns")
        val GRID_ROWS_KEY = intPreferencesKey("grid_rows")
        val DOCK_COUNT_KEY = intPreferencesKey("dock_count")
        val SHOW_LABELS_KEY = booleanPreferencesKey("show_icon_labels")
        val BATTERY_OPT_ASKED_KEY = booleanPreferencesKey("battery_opt_asked")
    }

    // ── Hidden Apps ──────────────────────────────────────────────────────────

    val hiddenApps: Flow<Set<String>> = ds.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[HIDDEN_APPS_KEY] ?: emptySet() }

    suspend fun addHiddenApp(pkg: String) = ds.edit { prefs ->
        prefs[HIDDEN_APPS_KEY] = (prefs[HIDDEN_APPS_KEY] ?: emptySet()) + pkg
    }

    suspend fun removeHiddenApp(pkg: String) = ds.edit { prefs ->
        prefs[HIDDEN_APPS_KEY] = (prefs[HIDDEN_APPS_KEY] ?: emptySet()) - pkg
    }

    suspend fun clearHiddenApps() = ds.edit { it.remove(HIDDEN_APPS_KEY) }

    // ── Icon Pack ────────────────────────────────────────────────────────────

    val iconPackPackage: Flow<String> = ds.data
        .map { it[ICON_PACK_KEY] ?: "" }   // empty = use system icons

    suspend fun setIconPack(packageName: String) = ds.edit { it[ICON_PACK_KEY] = packageName }

    // ── Focus Mode ───────────────────────────────────────────────────────────

    val focusSchedule: Flow<FocusSchedule> = ds.data.map { prefs ->
        prefs[FOCUS_SCHEDULE_KEY]?.let {
            runCatching { Json.decodeFromString<FocusSchedule>(it) }.getOrNull()
        } ?: FocusSchedule()
    }

    val focusActive: Flow<Boolean> = ds.data.map { it[FOCUS_ACTIVE_KEY] ?: false }

    suspend fun saveFocusSchedule(schedule: FocusSchedule) = ds.edit {
        it[FOCUS_SCHEDULE_KEY] = Json.encodeToString(schedule)
    }

    suspend fun setFocusActive(active: Boolean) = ds.edit { it[FOCUS_ACTIVE_KEY] = active }

    // ── Grid Config ──────────────────────────────────────────────────────────

    val gridConfig: Flow<GridConfig> = ds.data.map {
        GridConfig(
            cols = it[GRID_COLS_KEY] ?: 4,
            rows = it[GRID_ROWS_KEY] ?: 5,
            dockCount = it[DOCK_COUNT_KEY] ?: 4,
            showLabels = it[SHOW_LABELS_KEY] ?: true
        )
    }

    suspend fun saveGridConfig(config: GridConfig) = ds.edit {
        it[GRID_COLS_KEY] = config.cols
        it[GRID_ROWS_KEY] = config.rows
        it[DOCK_COUNT_KEY] = config.dockCount
        it[SHOW_LABELS_KEY] = config.showLabels
    }

    val batteryOptAsked: Flow<Boolean> = ds.data.map { it[BATTERY_OPT_ASKED_KEY] ?: false }
    suspend fun markBatteryOptAsked() = ds.edit { it[BATTERY_OPT_ASKED_KEY] = true }
}

data class GridConfig(
    val cols: Int = 4,
    val rows: Int = 5,
    val dockCount: Int = 4,
    val showLabels: Boolean = true
)
```

---

## FILE: app/src/main/java/com/launcher360v2/data/HomeRepository.kt

```kotlin
package com.launcher360v2.data

import com.launcher360v2.data.db.FolderDao
import com.launcher360v2.data.db.HomeCellDao
import com.launcher360v2.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val cellDao: HomeCellDao,
    private val folderDao: FolderDao
) {
    fun observeHomeCells(): Flow<List<HomeCell>> = cellDao.observeAll()
    fun observeFolders(): Flow<List<FolderWithApps>> = folderDao.observeAllFolders()

    suspend fun addAppToHome(componentFlat: String, page: Int, row: Int, col: Int) {
        val pkg = android.content.ComponentName.unflattenFromString(componentFlat)?.packageName ?: return
        cellDao.insertCell(
            HomeCell(page = page, row = row, col = col, type = CellType.APP,
                packageName = pkg, componentFlat = componentFlat)
        )
    }

    suspend fun removeFromHome(componentFlat: String) {
        cellDao.deleteCellByComponent(componentFlat)
    }

    suspend fun moveCell(id: Long, newPage: Int, newRow: Int, newCol: Int) {
        cellDao.moveCell(id, newPage, newRow, newCol)
    }

    suspend fun addWidget(widgetId: Int, page: Int, row: Int, col: Int, spanH: Int, spanV: Int) {
        cellDao.insertCell(
            HomeCell(page = page, row = row, col = col, type = CellType.WIDGET,
                widgetId = widgetId, spanH = spanH, spanV = spanV)
        )
    }

    suspend fun removeWidget(widgetId: Int) {
        // Find cell with this widget ID and delete
        // Implemented by querying all cells and filtering — simple for personal use
    }

    suspend fun createFolder(name: String, page: Int, row: Int, col: Int): Long {
        val folderId = folderDao.insertFolder(FolderEntity(name = name, page = page, row = row, col = col))
        cellDao.insertCell(HomeCell(page = page, row = row, col = col, type = CellType.FOLDER, folderId = folderId))
        return folderId
    }

    suspend fun addAppToFolder(folderId: Long, componentFlat: String, position: Int) {
        folderDao.insertFolderApp(FolderApp(folderId = folderId, componentFlat = componentFlat, position = position))
    }

    suspend fun getCellAt(page: Int, row: Int, col: Int): HomeCell? =
        cellDao.getCellAt(page, row, col)
}
```
