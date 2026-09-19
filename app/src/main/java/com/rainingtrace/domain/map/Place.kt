package com.rainingtrace.domain.map

/**
 * RT-DOM-003: 地点与地图实体。
 *
 * 设计原则（GDD §08）：地图上没有"纯背景地点"，
 * 每个实体至少拥有一个可执行动作。
 */

/**
 * 地图实体的呈现分组。**单一真相挂在类型上**，不在 Place 上再存一份。
 *
 * - [PLACE]：人文地点（手工配置，有名字与描述，常驻）；
 * - [RESOURCE]：自然资源点（林/丛/菌这类"到时候有东西可采"的点）。
 *   现在也是手工配的；将来由"世界状态 + 种子"生成、可刷新（见 handoff 的设计结论）。
 *   分组决定筛选面板归到哪一行、要不要进"附近地点"列表、未探索时是否渲染。
 */
enum class PlaceCategory {
    PLACE,
    RESOURCE,
}

/** 地点类型。新增类型会自动多出一个地图图标层（渲染层按类型遍历建层）。 */
enum class PlaceType(val category: PlaceCategory) {
    LAKE(PlaceCategory.PLACE),
    LIBRARY(PlaceCategory.PLACE),
    CANTEEN(PlaceCategory.PLACE),
    DORM(PlaceCategory.PLACE),
    GARDEN(PlaceCategory.PLACE),
    PLAZA(PlaceCategory.PLACE),
    OTHER(PlaceCategory.PLACE),

    /** 果林：秋天掉果子。 */
    ORCHARD(PlaceCategory.RESOURCE),

    /** 浆果丛：走过顺手就能摘。 */
    BERRY_BUSH(PlaceCategory.RESOURCE),

    /** 菌丛：雨后长得最好。 */
    MUSHROOM_PATCH(PlaceCategory.RESOURCE),
}

/** 地点动作：观察（看）与采集（拿）是两条不同的行为，产出规则按动作分开配。 */
enum class PlaceActionType {
    OBSERVE,
    COLLECT,
}

data class Place(
    val id: String,
    val name: String,
    val type: PlaceType,
    val coordinate: WorldCoordinate,
    val actions: Set<PlaceActionType>,
    val description: String = "",
) {
    init {
        require(id.isNotBlank()) { "place id must not be blank" }
        require(name.isNotBlank()) { "place name must not be blank" }
        require(actions.isNotEmpty()) { "place must have at least one action: $name" }
    }
}

interface PlaceRepository {
    suspend fun placeById(id: String): Place?

    /** 半径内地点，按距离升序。 */
    suspend fun nearby(coordinate: WorldCoordinate, radiusMeters: Double): List<Place>
}
