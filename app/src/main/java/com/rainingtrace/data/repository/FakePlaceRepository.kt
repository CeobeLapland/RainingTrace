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
            description = "校区中轴最北侧的一潭水。雨后的傍晚，这里常有麻雀贴着水面掠过。",
        )

        // 以下坐标为校区内参考值，真机试玩后校准。
        val LIBRARY = Place(
            id = "place.bit.library",
            name = "图书馆",
            type = PlaceType.LIBRARY,
            coordinate = WorldCoordinate(39.7302, 116.1690),
            actions = setOf(PlaceActionType.OBSERVE),
            description = "安静的大楼，午后阳光会斜斜地落在中庭。",
        )

        val CANTEEN = Place(
            id = "place.bit.canteen_one",
            name = "食堂一",
            type = PlaceType.CANTEEN,
            coordinate = WorldCoordinate(39.7294, 116.1726),
            actions = setOf(PlaceActionType.OBSERVE),
            description = "饭点前总飘着饭菜香，窗口前排着长长的队。",
        )

        val PLAZA = Place(
            id = "place.bit.plaza",
            name = "中心广场",
            type = PlaceType.PLAZA,
            coordinate = WorldCoordinate(39.7310, 116.1735),
            actions = setOf(PlaceActionType.OBSERVE),
            description = "开阔的广场，周末偶尔会有社团活动。",
        )

        val GARDEN = Place(
            id = "place.bit.garden",
            name = "湖心花园",
            type = PlaceType.GARDEN,
            coordinate = WorldCoordinate(39.7338, 116.1732),
            actions = setOf(PlaceActionType.OBSERVE),
            description = "一条小径绕着几棵老树，落叶和青苔安安静静地铺着。",
        )

        val DORM = Place(
            id = "place.bit.dorm_3",
            name = "学生宿舍 3 号楼",
            type = PlaceType.DORM,
            coordinate = WorldCoordinate(39.7290, 116.1712),
            actions = setOf(PlaceActionType.OBSERVE),
            description = "晚上亮着许多窗，是回来的地方。",
        )

        val DEFAULT_PLACES = listOf(
            NORTH_LAKE, LIBRARY, CANTEEN, PLAZA, GARDEN, DORM,
        )
    }
}
