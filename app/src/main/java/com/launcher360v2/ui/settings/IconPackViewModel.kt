package com.launcher360v2.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher360v2.data.PrefsRepository
import com.launcher360v2.data.model.IconPackInfo
import com.launcher360v2.domain.IconCache
import com.launcher360v2.domain.IconPackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [IconPackScreen]. Discovers installed icon packs and lets the user pick
 * one; the selection is persisted in [PrefsRepository] and applied through
 * [IconPackManager]. The active pack is driven by DataStore so it survives
 * process death.
 */
@HiltViewModel
class IconPackViewModel @Inject constructor(
    private val iconPackManager: IconPackManager,
    private val iconCache: IconCache,
    private val prefs: PrefsRepository
) : ViewModel() {

    private val _installedPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val installedPacks: StateFlow<List<IconPackInfo>> = _installedPacks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val activePack: StateFlow<String> = prefs.iconPackPackage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        viewModelScope.launch {
            _isLoading.value = true
            _installedPacks.value = iconPackManager.getInstalledIconPacks()
            // Apply whatever pack is currently persisted so getIcon() works immediately.
            val current = prefs.iconPackPackage.first()
            iconPackManager.loadPack(current)
            _isLoading.value = false
        }
    }

    fun selectPack(packageName: String) {
        viewModelScope.launch {
            prefs.setIconPack(packageName)
            iconPackManager.loadPack(packageName)
            iconCache.clear()   // drop cached system icons so the pack is picked up
        }
    }
}
