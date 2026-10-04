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

    // ---- 户外自然（采集型地点）----
    // 设计稿 `places_design.md` 的 G 区与相关自然条目都落在这一组上。

    /** 林地：松针、竹、蘑菇。 */
    FOREST(PlaceCategory.PLACE),

    /** 田地：菜圃、稻田。 */
    FIELD(PlaceCategory.PLACE),

    /** 池塘：荷叶、鸭舌草。 */
    POND(PlaceCategory.PLACE),

    /** 湿地：香蒲、水藻。 */
    WETLAND(PlaceCategory.PLACE),

    /** 水边：贝壳、海草。 */
    SHORE(PlaceCategory.PLACE),

    /** 山丘：野菊、草坡。 */
    HILL(PlaceCategory.PLACE),

    /** 温室：苗、湿土。 */
    GREENHOUSE(PlaceCategory.PLACE),

    /** 道路：落叶、传单。 */
    STREET(PlaceCategory.PLACE),

    /** 小径：荒径上的旧物。 */
    PATH(PlaceCategory.PLACE),

    /** 桥：桥边的东西。 */
    BRIDGE(PlaceCategory.PLACE),
}

/**
 * 地点动作：观察（看）与采集（拿）是两条不同的行为，产出规则按动作分开配。
 *
 * 产出类动作按"手要怎么动"分开：动手拿（采集/收获/拾取）、贴水边（垂钓/取水）、
 * 走进去看（探索/观看）。分开配规则，同一个地点在不同动作下能给出不同的东西。
 */
enum class PlaceActionType {
    OBSERVE,
    COLLECT,

    /** 收获：果园、田里长成的。 */
    HARVEST,

    /** 拾取：地上顺手捡的。 */
    GATHER,

    /** 垂钓：要站到水边。 */
    FISH,

    /** 取水：要站到水边。 */
    WATER,

    /** 探索：走进边缘地带，顺带发现东西。 */
    EXPLORE,

    /** 观看：看人做事、旁听。 */
    WATCH,
}

/**
 * 地点从哪来。**呈现与筛选语义挂在它上面，不要只看 `PlaceType.category`**：
 * 刷出来的点完全可能是林地/山丘这类 PLACE 类型，但它既不该"未揭示时画灰问号"
 * （那是免费开图），也不该进"附近地点"列表（会淹没列表）。
 */
enum class PlaceOrigin {
    /** 人配（`places.json`）或玩家现场记点——常驻，不过期。 */
    AUTHORED,

    /** 按刷新规则 + 确定性种子生成——会过期、会换位置（见 `SpawnPlanner`）。 */
    SPAWNED,
}

data class Place(
    val id: String,
    val name: String,
    val type: PlaceType,
    val coordinate: WorldCoordinate,
    val actions: Set<PlaceActionType>,
    val description: String = "",
    /** 来源；[PlaceOrigin.SPAWNED] 的点由规则算出，不落盘。 */
    val origin: PlaceOrigin = PlaceOrigin.AUTHORED,
    /** 过期时刻（仅 spawn 点有）；常驻地点为 null。 */
    val expiresAtEpochMs: Long? = null,
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

    /** 全部地点；给"按 id 解析坐标"的离线用途（如 NPC 作息），不是玩法查询。 */
    suspend fun all(): List<Place>
}
