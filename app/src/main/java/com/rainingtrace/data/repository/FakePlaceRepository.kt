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

    override suspend fun all(): List<Place> = DEFAULT_PLACES

    companion object {
        // 可执行的动作集合：每个地点至少一个；产出由 domain 的规则表决定，这里只管"能不能做"。
        private val OBSERVE_AND_COLLECT = setOf(PlaceActionType.OBSERVE, PlaceActionType.COLLECT)

        /** 自然资源点只给采集：它们是"去拿东西"的点，不是"看风景"的地点。 */
        private val COLLECT_ONLY = setOf(PlaceActionType.COLLECT)

        // 北湖：校区中轴最北侧（公开资料参考值）
        val NORTH_LAKE = Place(
            id = "place.bit.north_lake",
            name = "北湖",
            type = PlaceType.LAKE,
            coordinate = WorldCoordinate(39.7326, 116.1712),
            actions = OBSERVE_AND_COLLECT,
            description = "校区中轴最北侧的一潭水。岸边有芦苇，雨后的傍晚常有麻雀贴着水面掠过。",
        )

        // 以下坐标为校区内参考值，真机试玩后校准。
        val LIBRARY = Place(
            id = "place.bit.library",
            name = "图书馆",
            type = PlaceType.LIBRARY,
            coordinate = WorldCoordinate(39.7302, 116.1690),
            actions = OBSERVE_AND_COLLECT,
            description = "安静的大楼，午后阳光会斜斜地落在中庭。书架间偶尔能翻出旧书目卡。",
        )

        val CANTEEN = Place(
            id = "place.bit.canteen_one",
            name = "食堂一",
            type = PlaceType.CANTEEN,
            coordinate = WorldCoordinate(39.7294, 116.1726),
            actions = OBSERVE_AND_COLLECT,
            description = "饭点前总飘着饭菜香，窗口前排着长长的队。窗口边挂着今日的菜签。",
        )

        val PLAZA = Place(
            id = "place.bit.plaza",
            name = "中心广场",
            type = PlaceType.PLAZA,
            coordinate = WorldCoordinate(39.7310, 116.1735),
            actions = OBSERVE_AND_COLLECT,
            description = "开阔的广场，周末偶尔会有社团活动。角落里常落下几张传单。",
        )

        val GARDEN = Place(
            id = "place.bit.garden",
            name = "湖心花园",
            type = PlaceType.GARDEN,
            coordinate = WorldCoordinate(39.7338, 116.1732),
            actions = OBSERVE_AND_COLLECT,
            description = "一条小径绕着几棵老树。苔藓、松果与花瓣随季节换着铺在地上。",
        )

        val DORM = Place(
            id = "place.bit.dorm_3",
            name = "学生宿舍 3 号楼",
            type = PlaceType.DORM,
            coordinate = WorldCoordinate(39.7290, 116.1712),
            actions = OBSERVE_AND_COLLECT,
            description = "晚上亮着许多窗，是回来的地方。桌边总贴着几张写到一半的便签。",
        )

        // ---- 自然资源点 ----
        // 现在是手工配的，所以坐标都挑在花园/湖边这些人真会走到的地方。
        // 将来交给"世界状态 + 种子"生成时，位置约束（别落在马路上）是主要难点，不是技术问题。

        val ORCHARD = Place(
            id = "place.bit.orchard",
            name = "果林",
            type = PlaceType.ORCHARD,
            coordinate = WorldCoordinate(39.7346, 116.1742),
            actions = COLLECT_ONLY,
            description = "花园北侧的一小片果木，秋天枝头最沉。",
        )

        val BERRY_BUSH = Place(
            id = "place.bit.berry_bush",
            name = "浆果丛",
            type = PlaceType.BERRY_BUSH,
            coordinate = WorldCoordinate(39.7341, 116.1726),
            actions = COLLECT_ONLY,
            description = "小径旁的一丛灌木，走过去顺手就能摘几颗。",
        )

        val MUSHROOM_PATCH = Place(
            id = "place.bit.mushroom_patch",
            name = "菌丛",
            type = PlaceType.MUSHROOM_PATCH,
            coordinate = WorldCoordinate(39.7334, 116.1738),
            actions = COLLECT_ONLY,
            description = "老树根边常冒菌子，下过雨那几天最好找。",
        )

        val DEFAULT_PLACES = listOf(
            NORTH_LAKE, LIBRARY, CANTEEN, PLAZA, GARDEN, DORM,
            ORCHARD, BERRY_BUSH, MUSHROOM_PATCH,
        )

        /**
         * 地点的口语别名 → placeId，给 NPC 消息解析用（玩家不会说"湖心花园"的全名）。
         *
         * 放这里而不是 domain：别名属于内容，和地点配置一起维护；
         * 匹配时要**先长后短**（"湖心花园"必须先于"花园"和"湖"命中）。
         */
        val PLACE_ALIASES: Map<String, String> = mapOf(
            "北湖" to NORTH_LAKE.id,
            "湖边" to NORTH_LAKE.id,
            "湖" to NORTH_LAKE.id,
            "图书馆" to LIBRARY.id,
            "馆里" to LIBRARY.id,
            "食堂一" to CANTEEN.id,
            "食堂" to CANTEEN.id,
            "中心广场" to PLAZA.id,
            "广场" to PLAZA.id,
            "湖心花园" to GARDEN.id,
            "花园" to GARDEN.id,
            "宿舍" to DORM.id,
            "果林" to ORCHARD.id,
            "浆果丛" to BERRY_BUSH.id,
            "菌丛" to MUSHROOM_PATCH.id,
        )
    }
}
