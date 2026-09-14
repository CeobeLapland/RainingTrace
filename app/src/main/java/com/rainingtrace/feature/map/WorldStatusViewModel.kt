package com.rainingtrace.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WorldStatusUiState(
    /** 例："14:32"。 */
    val timeLabel: String = "--:--",
    /** 例："晴" / "小雨"。 */
    val weatherLabel: String = "未知",
)

/**
 * 世界状态接线（GDD §07）：时间 + 天气。
 * MVP 用 FakeWeatherProvider（固定晴），随时间轮询刷新；
 * P1 接真实天气 API 后本 VM 不变，只换 provider。
 */
class WorldStatusViewModel(
    private val clock: WorldClock,
    private val weatherProvider: WeatherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorldStatusUiState())
    val uiState: StateFlow<WorldStatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _uiState.value = refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh(): WorldStatusUiState {
        val weather = weatherProvider.currentWeather()
        val time = TIME_FORMAT.format(clock.now().atZone(WEATHER_ZONE))
        return WorldStatusUiState(
            timeLabel = time,
            weatherLabel = weatherLabel(weather.kind),
        )
    }

    private fun weatherLabel(kind: WeatherKind): String = when (kind) {
        WeatherKind.CLEAR -> "晴"
        WeatherKind.CLOUDY -> "多云"
        WeatherKind.LIGHT_RAIN -> "小雨"
        WeatherKind.HEAVY_RAIN -> "大雨"
        WeatherKind.SNOW -> "雪"
        WeatherKind.FOG -> "雾"
        WeatherKind.WIND -> "风"
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
        private val WEATHER_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}