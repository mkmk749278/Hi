package com.launcher360v2.ui.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher360v2.data.AppRepository
import com.launcher360v2.data.PrefsRepository
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.domain.FocusModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val appRepo: AppRepository,
    private val prefs: PrefsRepository,
    private val focusMode: FocusModeManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Apps shown in the drawer — hidden apps and focus-mode-hidden apps are excluded.
     * Also filters by search query.
     */
    val drawerApps: StateFlow<List<AppItem>> = combine(
        appRepo.allApps,
        prefs.hiddenApps,
        focusMode.focusHiddenPackages,
        _searchQuery
    ) { apps, hidden, focusHidden, query ->
        apps
            .filter { it.packageName !in hidden }
            .filter { it.packageName !in focusHidden }
            .filter {
                query.isEmpty() ||
                        it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusActive: StateFlow<Boolean> = focusMode.isEffectivelyActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun launchApp(item: AppItem) = appRepo.launchApp(item)

    fun hideApp(packageName: String) = viewModelScope.launch {
        prefs.addHiddenApp(packageName)
    }

    fun openAppInfo(item: AppItem) = appRepo.openAppInfo(item)

    fun toggleFocusMode() = viewModelScope.launch {
        val current = focusActive.value
        focusMode.setManualActive(!current)
    }
}
