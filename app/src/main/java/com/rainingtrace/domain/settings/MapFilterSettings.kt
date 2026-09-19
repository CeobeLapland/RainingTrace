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

/**
 * 由"隐藏集合"反推"显示集合"。
 *
 * 持久化存的应该是**隐藏项**而不是显示项：这样以后新增的地点类型默认可见。
 * 如果存显示项，老安装升级后新类型不在那份集合里，会被莫名其妙地藏掉
 * （而且筛选面板上还看不出来是"关着"的）。
 */
fun shownPlaceTypesFrom(hidden: Set<PlaceType>): Set<PlaceType> =
    PlaceType.entries.filterNot { it in hidden }.toSet()
