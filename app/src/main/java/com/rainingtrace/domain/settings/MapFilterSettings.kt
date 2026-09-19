package com.rainingtrace.domain.settings

import com.rainingtrace.domain.map.PlaceType

/** 记忆时间筛选：全部 / 今天 / 近一周。 */
enum class MemoryTimeFilter {
    ALL,
    TODAY,
    THIS_WEEK,
    ;

    companion object {
        val DEFAULT = ALL
    }
}

/**
 * 地图图层筛选（逻辑隐藏语义）：被关掉的类型/时间不渲染、不进附近列表、点不到。
 * 属于本地偏好，重启保留。
 */
data class MapFilterSettings(
    val shownPlaceTypes: Set<PlaceType> = PlaceType.entries.toSet(),
    val showMemories: Boolean = true,
    val memoryTimeFilter: MemoryTimeFilter = MemoryTimeFilter.DEFAULT,
)
