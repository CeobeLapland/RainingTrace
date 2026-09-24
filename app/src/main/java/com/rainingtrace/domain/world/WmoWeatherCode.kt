package com.rainingtrace.domain.world

/**
 * WMO 4677 天气码 → [WeatherKind]。
 *
 * 这是 Open-Meteo `current.weather_code` 的语义。表本身是标准表，所以是纯函数、可单测。
 *
 * 注意 **`WeatherKind.WIND` 永远不会被这里返回**：WMO 4677 里"风"是一个独立变量
 * （`wind_speed_10m`），不是一个天气码。所以风天只能靠手动覆盖设置，不是漏了。
 *
 * 未知码返回 `null` 而不是猜一个默认值：调用方据此**保留上一次的好值**，
 * 比把"不知道"说成"晴"诚实。
 */
fun weatherKindOfWmoCode(code: Int): WeatherKind? = when (code) {
    0, 1 -> WeatherKind.CLEAR
    2, 3 -> WeatherKind.CLOUDY
    45, 48 -> WeatherKind.FOG

    // 毛毛雨 / 冻雨（轻） / 小到中雨 / 小到中阵雨
    51, 53, 55, 56, 57, 61, 63, 80, 81 -> WeatherKind.LIGHT_RAIN

    // 大雨 / 冻雨（重） / 强阵雨 / 雷暴（含冰雹）
    65, 66, 67, 82, 95, 96, 99 -> WeatherKind.HEAVY_RAIN

    // 各类降雪（含米雪与阵雪）
    71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW

    else -> null
}