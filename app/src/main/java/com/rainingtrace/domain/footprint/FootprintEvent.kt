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
}
