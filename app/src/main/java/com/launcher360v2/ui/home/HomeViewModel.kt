package com.launcher360v2.ui.home

import android.content.ComponentName
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher360v2.data.AppRepository
import com.launcher360v2.data.HomeRepository
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.data.model.HomeCell
import com.launcher360v2.domain.IconCache
import com.launcher360v2.domain.PredictiveAppsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepo: HomeRepository,
    private val appRepo: AppRepository,
    private val iconCache: IconCache,
    private val predictive: PredictiveAppsEngine
) : ViewModel() {

    // Full home layout from Room
    val homeCells: StateFlow<List<HomeCell>> = homeRepo.observeHomeCells()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All apps (resolved for icon lookup)
    private val allApps: StateFlow<List<AppItem>> = appRepo.allApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Predictive bar suggestions — recomputes whenever the app list changes.
    val predictiveSuggestions: StateFlow<List<AppItem>> = allApps
        .mapLatest { apps ->
            val packages = predictive.getSuggestions(4)
            packages.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Helper: resolve a ComponentName to AppItem for rendering
    fun resolveApp(componentFlat: String): AppItem? {
        val cn = ComponentName.unflattenFromString(componentFlat) ?: return null
        return allApps.value.firstOrNull { it.componentName == cn }
    }

    fun launchApp(item: AppItem) {
        appRepo.launchApp(item)
    }

    fun addToHome(componentFlat: String, page: Int, row: Int, col: Int) {
        viewModelScope.launch {
            homeRepo.addAppToHome(componentFlat, page, row, col)
        }
    }

    fun removeFromHome(componentFlat: String) {
        viewModelScope.launch {
            homeRepo.removeFromHome(componentFlat)
        }
    }
}
