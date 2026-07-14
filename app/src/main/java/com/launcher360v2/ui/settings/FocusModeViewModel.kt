package com.launcher360v2.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher360v2.data.AppRepository
import com.launcher360v2.data.model.AppItem
import com.launcher360v2.data.model.FocusSchedule
import com.launcher360v2.domain.FocusModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [FocusModeScreen]. Wraps [FocusModeManager] to expose the current
 * schedule + effective active state, and the full app list used to pick
 * distraction apps.
 */
@HiltViewModel
class FocusModeViewModel @Inject constructor(
    private val focusMode: FocusModeManager,
    appRepo: AppRepository
) : ViewModel() {

    val schedule: StateFlow<FocusSchedule> = focusMode.schedule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusSchedule())

    val isActive: StateFlow<Boolean> = focusMode.isEffectivelyActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val allApps: StateFlow<List<AppItem>> = appRepo.allApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFocusActive(active: Boolean) = viewModelScope.launch {
        focusMode.setManualActive(active)
    }

    fun saveSchedule(schedule: FocusSchedule) = viewModelScope.launch {
        focusMode.saveSchedule(schedule)
    }
}
