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
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[ICON_PACK_KEY] ?: "" }   // empty = use system icons

    suspend fun setIconPack(packageName: String) = ds.edit { it[ICON_PACK_KEY] = packageName }

    // ── Focus Mode ───────────────────────────────────────────────────────────

    val focusSchedule: Flow<FocusSchedule> = ds.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[FOCUS_SCHEDULE_KEY]?.let {
                runCatching { Json.decodeFromString<FocusSchedule>(it) }.getOrNull()
            } ?: FocusSchedule()
        }

    val focusActive: Flow<Boolean> = ds.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[FOCUS_ACTIVE_KEY] ?: false }

    suspend fun saveFocusSchedule(schedule: FocusSchedule) = ds.edit {
        it[FOCUS_SCHEDULE_KEY] = Json.encodeToString(schedule)
    }

    suspend fun setFocusActive(active: Boolean) = ds.edit { it[FOCUS_ACTIVE_KEY] = active }

    // ── Grid Config ──────────────────────────────────────────────────────────

    val gridConfig: Flow<GridConfig> = ds.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
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

    val batteryOptAsked: Flow<Boolean> = ds.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[BATTERY_OPT_ASKED_KEY] ?: false }
    suspend fun markBatteryOptAsked() = ds.edit { it[BATTERY_OPT_ASKED_KEY] = true }
}

data class GridConfig(
    val cols: Int = 4,
    val rows: Int = 5,
    val dockCount: Int = 4,
    val showLabels: Boolean = true
)
