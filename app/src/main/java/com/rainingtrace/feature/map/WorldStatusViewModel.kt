package com.rainingtrace.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.core.ui.label
import com.rainingtrace.domain.world.WorldStateProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter

data class WorldStatusUiState(
    /** 例："14:32"。 */
    val timeLabel: String = "--:--",
    /** 例："晴" / "小雨"。 */
    val weatherLabel: String = "未知",
    /** 例："白天" / "夜晚"。 */
    val timeOfDayLabel: String = "",
)

/**
 * 世界状态接线（GDD §07）：时间 + 天气 + 时段。
 *
 * 不再自己轮询时钟/天气——[WorldStateProvider] 已是唯一的真值源，
 * 这里只把快照映射成展示文案。采集由 UI 的 collectAsStateWithLifecycle 驱动：
 * 界面不可见就没有订阅者，provider 的 tick 随之停下（后台不空转）。
 */
class WorldStatusViewModel(
    worldState: WorldStateProvider,
) : ViewModel() {

    val uiState: StateFlow<WorldStatusUiState> = worldState.state
        .map { state ->
            WorldStatusUiState(
                timeLabel = TIME_FORMAT.format(state.instant.atZone(WORLD_ZONE)),
                weatherLabel = state.weather.kind.label(),
                timeOfDayLabel = state.timeOfDay.label(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = WorldStatusUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}