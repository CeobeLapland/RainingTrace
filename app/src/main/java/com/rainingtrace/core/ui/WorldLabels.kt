package com.rainingtrace.core.ui

import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WorldCondition

/** 地点类型的中文标签（地图卡片、图鉴的"来源"共用）。 */
fun PlaceType.label(): String = when (this) {
    PlaceType.LAKE -> "湖泊"
    PlaceType.LIBRARY -> "图书馆"
    PlaceType.CANTEEN -> "食堂"
    PlaceType.DORM -> "宿舍"
    PlaceType.GARDEN -> "花园"
    PlaceType.PLAZA -> "广场"
    PlaceType.OTHER -> "地点"
    PlaceType.ORCHARD -> "果林"
    PlaceType.BERRY_BUSH -> "浆果丛"
    PlaceType.MUSHROOM_PATCH -> "菌丛"
}

/**
 * 世界条件的中文描述（图鉴"来源"那行用）：组合条件用 `·` 连接，否定写成 `非…`。
 * 纯展示，所以放在 core/ui 而不是 domain（domain 只给结构，见 `ResourceSource`）。
 */
fun WorldCondition.describe(): String = when (this) {
    is WorldCondition.WeatherIn -> kinds.sortedBy { it.ordinal }.joinToString("/") { it.label() }
    is WorldCondition.TimeOfDayIn -> times.sortedBy { it.ordinal }.joinToString("/") { it.label() }
    is WorldCondition.SeasonIn -> seasons.sortedBy { it.ordinal }.joinToString("/") { it.label() }
    is WorldCondition.BetweenMinutes ->
        "${hourMinute(startMinuteOfDay)}–${hourMinute(endMinuteOfDay)}"

    is WorldCondition.All -> conditions.joinToString("·") { it.describe() }
    is WorldCondition.Not -> "非${condition.describe()}"
}

private fun hourMinute(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

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