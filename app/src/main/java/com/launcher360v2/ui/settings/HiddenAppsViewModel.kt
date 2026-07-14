package com.launcher360v2.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher360v2.data.AppRepository
import com.launcher360v2.data.PrefsRepository
import com.launcher360v2.data.model.AppItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [HiddenAppsScreen]. Exposes the full (unfiltered) app list plus the
 * persisted set of hidden package names, mirroring the DrawerViewModel pattern
 * but WITHOUT applying the hidden filter — this screen must show every app.
 */
@HiltViewModel
class HiddenAppsViewModel @Inject constructor(
    private val appRepo: AppRepository,
    private val prefs: PrefsRepository
) : ViewModel() {

    val allApps: StateFlow<List<AppItem>> = appRepo.allApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenApps: StateFlow<Set<String>> = prefs.hiddenApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun hideApp(packageName: String) = viewModelScope.launch {
        prefs.addHiddenApp(packageName)
    }

    fun showApp(packageName: String) = viewModelScope.launch {
        prefs.removeHiddenApp(packageName)
    }
}
