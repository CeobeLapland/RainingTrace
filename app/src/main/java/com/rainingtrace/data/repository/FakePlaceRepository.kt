package com.rainingtrace.data.repository

import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo

/**
 * 内存实现：地点全部来自构造参数。
 *
 * 内容本体住在 `assets/content/places.json`（+ 私有目录覆盖层），
 * 这里刻意**不再内置任何一份地点**——两份内容一定会漂移（见 `ContentStore`）。
 * 运行时用的是 `ContentPlaceRepository`；这个类留给测试与将来的内存态场景。
 */
class FakePlaceRepository(
    private val places: List<Place>,
) : PlaceRepository {

    private val byId: Map<String, Place> = places.associateBy { it.id }

    override suspend fun placeById(id: String): Place? = byId[id]

    override suspend fun nearby(coordinate: WorldCoordinate, radiusMeters: Double): List<Place> =
        byId.values
            .map { it to coordinate.distanceMetersTo(it.coordinate) }
            .filter { (_, d) -> d <= radiusMeters }
            .sortedBy { (_, d) -> d }
            .map { (place, _) -> place }

    override suspend fun all(): List<Place> = places
}