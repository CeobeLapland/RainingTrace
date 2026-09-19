package com.rainingtrace.core.ui

import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind

/** 世界状态的中文标签（地图 chip、设置调试区、日记卡片共用，避免文案漂移）。 */
fun WeatherKind.label(): String = when (this) {
    WeatherKind.CLEAR -> "晴"
    WeatherKind.CLOUDY -> "多云"
    WeatherKind.LIGHT_RAIN -> "小雨"
    WeatherKind.HEAVY_RAIN -> "大雨"
    WeatherKind.SNOW -> "雪"
    WeatherKind.FOG -> "雾"
    WeatherKind.WIND -> "风"
}

fun TimeOfDay.label(): String = when (this) {
    TimeOfDay.DAWN -> "黎明"
    TimeOfDay.DAY -> "白天"
    TimeOfDay.DUSK -> "黄昏"
    TimeOfDay.NIGHT -> "夜晚"
}

/** 季节未确定时给出明确文案，不要用"未知"糊过去。 */
fun Season?.label(): String = when (this) {
    Season.SPRING -> "春"
    Season.SUMMER -> "夏"
    Season.AUTUMN -> "秋"
    Season.WINTER -> "冬"
    null -> "季节未定"
}