package com.rainingtrace.domain.world

/**
 * RT-DOM-009: 世界天气状态。
 *
 * MVP 阶段不接真实天气 API（11_MVP §5），先用 Fake/手动设置。
 * 天气是"世界状态"，不是玩家属性；后续资源、事件、AR 都读取它。
 */
enum class WeatherKind {
    CLEAR,
    CLOUDY,
    LIGHT_RAIN,
    HEAVY_RAIN,
    SNOW,
    FOG,
    WIND,
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
        get() = kind == WeatherKind.LIGHT_RAIN || kind == WeatherKind.HEAVY_RAIN
}

/** 天气来源接口：真实 API / Fake 都实现它。 */
interface WeatherProvider {
    suspend fun currentWeather(): WeatherState
}

/** Fake 实现：固定或手动设置天气，供无网络开发与测试使用。 */
class FakeWeatherProvider(initial: WeatherState = CLEAR_DAY) : WeatherProvider {
    var weather: WeatherState = initial

    override suspend fun currentWeather(): WeatherState = weather

    companion object {
        val CLEAR_DAY = WeatherState(WeatherKind.CLEAR)
        val RAINY_DAY = WeatherState(WeatherKind.LIGHT_RAIN, humidity = 0.85, temperatureCelsius = 16.0)
    }
}
