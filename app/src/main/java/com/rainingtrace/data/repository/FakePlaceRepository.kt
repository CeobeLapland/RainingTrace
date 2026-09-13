package com.rainingtrace.data.repository

import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo

/**
 * Fake 实现：MVP 手工配置北理工良乡校区北校区地点。
 *
 * 坐标为公开资料参考值，真机试玩后校准。
 */
class FakePlaceRepository(
    places: List<Place> = DEFAULT_PLACES,
) : PlaceRepository {

    private val byId: Map<String, Place> = places.associateBy { it.id }

    override suspend fun placeById(id: String): Place? = byId[id]

    override suspend fun nearby(coordinate: WorldCoordinate, radiusMeters: Double): List<Place> =
        byId.values
            .map { it to coordinate.distanceMetersTo(it.coordinate) }
            .filter { (_, d) -> d <= radiusMeters }
            .sortedBy { (_, d) -> d }
            .map { (place, _) -> place }

    companion object {
        // 北湖：校区中轴最北侧（公开资料参考值）
        val NORTH_LAKE = Place(
            id = "place.bit.north_lake",
            name = "北湖",
            type = PlaceType.LAKE,
            coordinate = WorldCoordinate(39.7326, 116.1712),
            actions = setOf(PlaceActionType.OBSERVE),
        )

        val DEFAULT_PLACES = listOf(NORTH_LAKE)
    }
}
