package com.rainingtrace.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.map.GridLevel
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.BackgroundInterval
import com.rainingtrace.domain.settings.DayWindow
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.settings.NpcClockOffset
import com.rainingtrace.domain.settings.NpcMessageSettings
import com.rainingtrace.domain.settings.ProactiveLevel
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

    /** NPC 调试时间偏移（开发者模式）：只改变"NPC 此刻在哪"。 */
    val npcClockOffset = settings.npcClockOffset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NpcClockOffset.DEFAULT)

    /** 消息偏好：主动程度 + 是否显示好感数值。 */
    val npcMessages = settings.npcMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NpcMessageSettings())

    val canSetWeather: Boolean get() = mutableWeather != null

    val canSetSeason: Boolean get() = seasonSource != null

    val canSetTimeOfDay: Boolean get() = timeOfDaySource != null

    /** 当前天气：地图 chip、产出条件都跟着它走。 */
    val weatherKind: StateFlow<WeatherKind?> = mutableWeather?.weather
        ?.map { it.kind }
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutableWeather.weather.value.kind)
        ?: MutableStateFlow(null)

    /** 当前生效季节（手动覆盖优先，否则按节气推导）。 */
    val season: StateFlow<Season?> = seasonSource?.season
        ?: MutableStateFlow(null)

    /** 季节手动覆盖值；null = 按节气推导（UI 的"自动"行）。 */
    val seasonOverride: StateFlow<Season?> = seasonSource?.manualOverride
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

    fun setNpcClockOffset(offset: NpcClockOffset) {
        viewModelScope.launch { settings.setNpcClockOffset(offset) }
    }

    fun setProactiveLevel(level: ProactiveLevel) =
        updateNpcMessages { it.copy(proactiveLevel = level) }

    fun setShowAffection(show: Boolean) = updateNpcMessages { it.copy(showAffection = show) }

    /** 与 tracking 同构：读改写整份设置，字段不会互相覆盖。 */
    private fun updateNpcMessages(transform: (NpcMessageSettings) -> NpcMessageSettings) {
        viewModelScope.launch {
            settings.setNpcMessages(transform(settings.currentNpcMessages()))
        }
    }

    /** 读改写同一份设置：任何一项变化都整份落盘，字段不会互相覆盖。 */
    private fun updateTracking(transform: (TrackingSettings) -> TrackingSettings) {
        viewModelScope.launch {
            settings.setTracking(transform(settings.currentTracking()))
        }
    }
}
