package com.rainingtrace.domain.footprint

import com.rainingtrace.domain.map.WorldCoordinate

/**
 * RT-DOM-006: 足迹事件（append-only 历史）。
 *
 * 位置真相是连续坐标（战争迷雾架构：位置不吸附格子）；
 * 它也不是原始 GPS dump——上游轨迹点已经过去噪（06_地图专项 §6）。
 */
enum class FootprintEventType {
    CELL_REVEALED,
    CELL_VISITED,
    PLACE_OBSERVED,
    RESOURCE_ACQUIRED,
    MEMORY_CREATED,
    PHOTO_CAPTURED,

    /** 第一次遇见某个 NPC；payload 带 npcId，用于"见过谁"的去重。 */
    NPC_MET,

    /** 和某个 NPC 聊过一次（每次聊天一条）；payload 带 npcId。 */
    NPC_TALKED,

    /** NPC 主动发来一条消息；payload 带 npcId 与 ruleId，用于规则冷却去重。 */
    NPC_MESSAGE_SENT,

    /** 约定兑现了（玩家真的来了）。 */
    NPC_COMMITMENT_KEPT,

    /** 约好了但玩家没来；NPC 下次聊天会提一句。 */
    NPC_COMMITMENT_MISSED,
}

/** 默认仅自己可见；公开范围策略属于 P1 社交层。 */
enum class TraceVisibility {
    PRIVATE,
    FUZZED_PUBLIC,
}

data class FootprintEvent(
    val id: String,
    val timestampEpochMs: Long,
    val coordinate: WorldCoordinate,
    val eventType: FootprintEventType,
    val payload: Map<String, String> = emptyMap(),
    val visibility: TraceVisibility = TraceVisibility.PRIVATE,
)

/** 足迹事件仓储（append-only）。 */
interface FootprintRepository {
    suspend fun append(event: FootprintEvent)

    /** 按时间区间取事件，时间升序。 */
    suspend fun eventsBetween(fromEpochMs: Long, toEpochMs: Long): List<FootprintEvent>

    /**
     * 按类型取事件，时间升序。
     *
     * payload 在库里是单列编码，SQL 没法按 key 过滤，所以只按类型粗筛，
     * 具体的 payload 判定留给调用方（如"这个 NPC 见过没有"）。
     */
    suspend fun eventsOfType(type: FootprintEventType): List<FootprintEvent>
}
