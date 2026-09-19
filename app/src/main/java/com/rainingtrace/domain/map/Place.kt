package com.rainingtrace.domain.map

/**
 * RT-DOM-003: 地点。
 *
 * 设计原则（GDD §08）：地图上没有"纯背景地点"，
 * 每个 Place 至少拥有一个可执行动作。
 */
enum class PlaceType {
    LAKE,
    LIBRARY,
    CANTEEN,
    DORM,
    GARDEN,
    PLAZA,
    OTHER,
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
