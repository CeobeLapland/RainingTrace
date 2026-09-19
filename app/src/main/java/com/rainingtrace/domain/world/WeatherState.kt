package com.rainingtrace.domain.world

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RT-DOM-009: 世界天气状态。
 *
 * MVP 阶段不接真实天气 API（11_MVP §5），先用 Fake/手动设置。
 * 天气是"世界状态"，不是玩家属性；资源产出、事件、AR 都读它。
 *
 * 语义量挂在 kind 上（而不是散在别处 when）：加"雷雨"这类新天气时
 * 只要补一行并标好 [isRain]，条件与玩法不用改。
 */
enum class WeatherKind(val isRain: Boolean = false) {
    CLEAR,
    CLOUDY,
    LIGHT_RAIN(isRain = true),
    HEAVY_RAIN(isRain = true),
    SNOW,
    FOG,
    WIND,
    ;

    companion object {
        /** 所有会下雨的天气，供 "雨天限定" 这类条件直接引用。 */
        val RAINY: Set<WeatherKind> = entries.filter { it.isRain }.toSet()
    }
}

data class WeatherState(
    val kind: WeatherKind,
    /** 0.0~1.0，MVP 可固定。 */
    val humidity: Double = 0.5,
    /** 摄氏度，MVP 可固定。 */
    val temperatureCelsius: Double = 20.0,
) {
    init {
        require(humidity in 0.0..1.0) { "humidity out of range: $humidity" }
    }

    val isRaining: Boolean
        get() = kind.isRain
}

/**
 * 天气来源接口：真实 API / Fake 都实现它。
 *
 * 是 [StateFlow] 而不是"每次拉一次"：天气**变化**本身是玩法信号
 * （雨天事件、异常刷新、产出条件），UI 也不该靠轮询猜它有没有变。
 * 真实 API 版在内部按需拉取后 emit。
 */
interface WeatherProvider {
    val weather: StateFlow<WeatherState>
}

/** 可手动设定天气：调试/开发者模式用（真实 API 版不实现它）。 */
interface MutableWeatherProvider : WeatherProvider {
    fun setWeather(state: WeatherState)

    /** 只换天气类型，湿度/温度取 [weatherPreset] 给出的常规值。 */
    fun setKind(kind: WeatherKind)
}

/** Fake 实现：固定或手动设置天气，供无网络开发、调试与测试使用。 */
class FakeWeatherProvider(initial: WeatherState = CLEAR_DAY) : MutableWeatherProvider {

    private val _weather = MutableStateFlow(initial)
    override val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    override fun setWeather(state: WeatherState) {
        _weather.value = state
    }

    override fun setKind(kind: WeatherKind) {
        setWeather(weatherPreset(kind))
    }

    companion object {
        val CLEAR_DAY = WeatherState(WeatherKind.CLEAR)
        val RAINY_DAY = WeatherState(WeatherKind.LIGHT_RAIN, humidity = 0.85, temperatureCelsius = 16.0)
    }
}

/** 每种天气的常规体感值：假数据也要内部自洽，别出现雪天 20 度。 */
fun weatherPreset(kind: WeatherKind): WeatherState = when (kind) {
    WeatherKind.CLEAR -> WeatherState(kind, humidity = 0.45, temperatureCelsius = 24.0)
    WeatherKind.CLOUDY -> WeatherState(kind, humidity = 0.6, temperatureCelsius = 21.0)
    WeatherKind.LIGHT_RAIN -> WeatherState(kind, humidity = 0.85, temperatureCelsius = 16.0)
    WeatherKind.HEAVY_RAIN -> WeatherState(kind, humidity = 0.95, temperatureCelsius = 14.0)
    WeatherKind.SNOW -> WeatherState(kind, humidity = 0.8, temperatureCelsius = -2.0)
    WeatherKind.FOG -> WeatherState(kind, humidity = 0.9, temperatureCelsius = 12.0)
    WeatherKind.WIND -> WeatherState(kind, humidity = 0.4, temperatureCelsius = 18.0)
}