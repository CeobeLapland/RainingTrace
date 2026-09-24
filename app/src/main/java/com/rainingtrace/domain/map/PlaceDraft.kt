package com.rainingtrace.domain.map

/**
 * 现场采点（GDD §21 的开发者模式，GDD §08 的"每个地点都可做点什么"）。
 *
 * 玩家站在某处，把这里记成一个地点或资源点；写进覆盖层之后立刻生效，
 * 不用重编译、不用重装包。这是"手机比电脑更适合做的那一件事"——
 * 批量写坐标桌面完胜手机，但"我人就在这儿"只有手机知道。
 */
data class PlaceDraft(
    val name: String,
    val type: PlaceType,
    val coordinate: WorldCoordinate,
    val actions: Set<PlaceActionType>,
    val description: String = "",
)

/**
 * 默认动作：人文地点"看"和"拿"都有，自然资源点只给采集。
 * 与内置内容同一口径（见 `FakePlaceRepository` 的两组集合），
 * 所以自建地点和手工配的地点行为一致。
 */
fun defaultActionsFor(type: PlaceType): Set<PlaceActionType> = when (type.category) {
    PlaceCategory.PLACE -> setOf(PlaceActionType.OBSERVE, PlaceActionType.COLLECT)
    PlaceCategory.RESOURCE -> setOf(PlaceActionType.COLLECT)
}

/**
 * 草稿能不能落盘；null = 可以，否则是给玩家看的原因。
 * 文案是给玩家看的，不是给日志看的——所以直接说"哪里不行"。
 */
fun PlaceDraft.rejectionReason(): String? = when {
    name.isBlank() -> "还没给它起名字"
    actions.isEmpty() -> "至少要有一个可执行动作"
    else -> null
}

/**
 * 玩家自建地点的 id：`place.user.<毫秒>`。
 *
 * 与内置的 `place.bit.*` 天然分开，一眼能看出哪些是自己加的；
 * 撞车（同一毫秒提交两次）时加序号，保证 id 唯一。
 */
fun newPlaceId(nowMs: Long, taken: Set<String>): String {
    val base = "place.user.$nowMs"
    if (base !in taken) return base
    var suffix = 2
    while ("$base-$suffix" in taken) suffix++
    return "$base-$suffix"
}

sealed interface PlaceWriteResult {
    data class Added(val id: String) : PlaceWriteResult

    /** 失败一律带人话原因，绝不静默丢弃（采点失败却不说话是最糟的）。 */
    data class Rejected(val reason: String) : PlaceWriteResult
}

/** 写入端：把草稿落进内容覆盖层。 */
interface PlaceWriter {
    suspend fun addPlace(draft: PlaceDraft): PlaceWriteResult
}