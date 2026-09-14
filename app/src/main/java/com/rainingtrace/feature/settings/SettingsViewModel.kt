package com.rainingtrace.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.map.GridLevel
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.track.ChangeGridLevelUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    settings: AppSettingsRepository,
    private val changeGridLevel: ChangeGridLevelUseCase,
) : ViewModel() {

    val gridLevel = settings.gridLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridLevel.DEFAULT)

    fun selectGridLevel(level: GridLevel) {
        viewModelScope.launch { changeGridLevel(level) }
    }
}
