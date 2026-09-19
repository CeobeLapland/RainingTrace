package com.rainingtrace.domain.world

import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.core.time.WorldClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate

/** 一天中的时段：条件判定用（GDD §07"凌晨出现夜行生物"）。 */
enum class TimeOfDay { DAWN, DAY, DUSK, NIGHT }

/**
 * 季节。
 *
 * 划分口径**暂未定**（节气 or 月份），所以现在只留字段、不推导：
 * provider 给 null 表示"未确定"，季节条件在未确定时一律不满足。
 */
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

/**
 * 世界状态快照（GDD §07）：时间、天气、季节、节日一次给全。
 *
 * 这是"现实即输入"的入口——资源产出、NPC 出现、事件触发都应该读它，
 * 而不是各自去问时钟或天气。做成不可变值对象：便于单测、便于将来落成档案。
 */
data class WorldState(
    val instant: Instant,
    val localDate: LocalDate,
    /** 本地当天第几分钟（0..1439），条件判定直接用它，避免各处再格式化。 */
    val minuteOfDay: Int,
    val timeOfDay: TimeOfDay,
    val weather: WeatherState,
    /** null = 季节规则尚未确定（见 [Season]）。 */
    val season: Season? = null,
    /** 节日/现实事件 id；目前无来源，留作 P1 手工配置与服务端下发的接口位。 */
    val holiday: String? = null,
)

/**
 * 时段划分：先固定约定（05:00 黎明 / 08:00 白天 / 17:00 黄昏 / 20:00 夜晚）。
 * 以后要随季节与纬度的真实日出日落走，只改这一个函数即可。
 */
fun timeOfDayOf(minuteOfDay: Int): TimeOfDay = when (minuteOfDay) {
    in 5 * 60 until 8 * 60 -> TimeOfDay.DAWN
    in 8 * 60 until 17 * 60 -> TimeOfDay.DAY
    in 17 * 60 until 20 * 60 -> TimeOfDay.DUSK
    else -> TimeOfDay.NIGHT
}

/** 由时钟 + 天气 + 日历设定派生世界状态（纯函数，便于单测）。 */
fun deriveWorldState(
    instant: Instant,
    weather: WeatherState,
    season: Season? = null,
    holiday: String? = null,
): WorldState {
    val zoned = instant.atZone(WORLD_ZONE)
    val minuteOfDay = zoned.hour * 60 + zoned.minute
    return WorldState(
        instant = instant,
        localDate = zoned.toLocalDate(),
        minuteOfDay = minuteOfDay,
        timeOfDay = timeOfDayOf(minuteOfDay),
        weather = weather,
        season = season,
        holiday = holiday,
    )
}

/** 世界状态落进足迹事件 payload 的键（足迹/记忆共用同一口径）。 */
fun WorldState.footprintKeys(): Map<String, String> = buildMap {
    put("weather", weather.kind.name)
    put("timeOfDay", timeOfDay.name)
    season?.let { put("season", it.name) }
    holiday?.let { put("holiday", it) }
}

interface WorldStateProvider {
    /** 给 UI 的流：只在有人订阅时推进（与地图处理流同一省电口径）。 */
    val state: StateFlow<WorldState>

    /** 给玩法判定的一次性快照：按此刻现算，不会读到过期的 tick。 */
    fun current(): WorldState
}

/**
 * 系统实现：时钟 + 天气 + （暂未接入的）季节/节日。
 *
 * [state] 只在"分钟"粒度上变化，所以 30 秒 tick 足够；没人订阅时
 * [SharingStarted.WhileSubscribed] 会让 tick 停下来，后台不空转。
 */
class SystemWorldStateProvider(
    private val clock: WorldClock,
    private val weatherProvider: WeatherProvider,
    scope: CoroutineScope,
    /** 季节推导规则未定，先留空；将来的日历规则或开发者模式再注入。 */
    private val season: Season? = null,
    private val holiday: String? = null,
) : WorldStateProvider {

    override val state: StateFlow<WorldState> = combine(
        weatherProvider.weather,
        ticker(),
    ) { weather, _ -> snapshot(weather) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT_MS),
            initialValue = snapshot(weatherProvider.weather.value),
        )

    override fun current(): WorldState = snapshot(weatherProvider.weather.value)

    private fun snapshot(weather: WeatherState): WorldState =
        deriveWorldState(clock.now(), weather, season, holiday)

    private fun ticker() = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val SUBSCRIBE_STOP_TIMEOUT_MS = 5 * 60_000L
    }
}

/** 测试/Fake：整份世界状态手动设定（开发者模式调条件也用这个形态）。 */
class FakeWorldStateProvider(initial: WorldState) : WorldStateProvider {

    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<WorldState> = _state.asStateFlow()

    override fun current(): WorldState = _state.value

    fun set(state: WorldState) {
        _state.value = state
    }
}