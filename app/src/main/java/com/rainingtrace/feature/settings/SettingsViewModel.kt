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
import com.rainingtrace.domain.world.MutableWeatherProvider
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.SeasonSource
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.TimeOfDaySource
import com.rainingtrace.domain.world.WeatherKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: AppSettingsRepository,
    private val changeGridLevel: ChangeGridLevelUseCase,
    /** 可手动设定的天气 / 季节 / 时段；真实来源接上后传空，调试区自动隐藏。 */
    private val mutableWeather: MutableWeatherProvider? = null,
    private val seasonSource: SeasonSource? = null,
    private val timeOfDaySource: TimeOfDaySource? = null,
) : ViewModel() {

    val gridLevel = settings.gridLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridLevel.DEFAULT)

    val locationMode = settings.locationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationMode.DEFAULT)

    val tracking = settings.tracking
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingSettings())

    val canSetWeather: Boolean get() = mutableWeather != null

    val canSetSeason: Boolean get() = seasonSource != null

    val canSetTimeOfDay: Boolean get() = timeOfDaySource != null

    /** 当前天气：地图 chip、产出条件都跟着它走。 */
    val weatherKind: StateFlow<WeatherKind?> = mutableWeather?.weather
        ?.map { it.kind }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutableWeather.weather.value.kind)
        ?: MutableStateFlow(null)

    /** 当前季节；null = 未确定（季节限定的产出不会触发）。 */
    val season: StateFlow<Season?> = seasonSource?.season
        ?: MutableStateFlow(null)

    /** 时段覆盖；null = 按真实时间推导。 */
    val timeOfDayOverride: StateFlow<TimeOfDay?> = timeOfDaySource?.fixed
        ?: MutableStateFlow(null)

    fun selectGridLevel(level: GridLevel) {
        viewModelScope.launch { changeGridLevel(level) }
    }

    fun selectLocationMode(mode: LocationMode) {
        viewModelScope.launch { settings.setLocationMode(mode) }
    }

    fun setWeatherKind(kind: WeatherKind) {
        mutableWeather?.setKind(kind)
    }

    fun setSeason(season: Season?) {
        seasonSource?.setSeason(season)
    }

    fun setTimeOfDayOverride(timeOfDay: TimeOfDay?) {
        timeOfDaySource?.setFixed(timeOfDay)
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
