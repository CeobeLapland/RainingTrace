package com.rainingtrace.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.map.GridLevel
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.BackgroundInterval
import com.rainingtrace.domain.settings.DayWindow
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.settings.TrackingSettings
import com.rainingtrace.domain.track.ChangeGridLevelUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: AppSettingsRepository,
    private val changeGridLevel: ChangeGridLevelUseCase,
) : ViewModel() {

    val gridLevel = settings.gridLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridLevel.DEFAULT)

    val locationMode = settings.locationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationMode.DEFAULT)

    val tracking = settings.tracking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingSettings())

    fun selectGridLevel(level: GridLevel) {
        viewModelScope.launch { changeGridLevel(level) }
    }

    fun selectLocationMode(mode: LocationMode) {
        viewModelScope.launch { settings.setLocationMode(mode) }
    }

    fun setTrackingEnabled(enabled: Boolean) = updateTracking { it.copy(enabled = enabled) }

    fun setBackgroundInterval(interval: BackgroundInterval) =
        updateTracking { it.copy(backgroundInterval = interval) }

    fun setDaytimeOnly(daytimeOnly: Boolean) =
        updateTracking { it.copy(daytimeOnly = daytimeOnly) }

    fun setDayWindow(window: DayWindow) = updateTracking { it.copy(dayWindow = window) }

    /** 读改写同一份设置：任何一项变化都整份落盘，字段不会互相覆盖。 */
    private fun updateTracking(transform: (TrackingSettings) -> TrackingSettings) {
        viewModelScope.launch {
            settings.setTracking(transform(settings.currentTracking()))
        }
    }
}
