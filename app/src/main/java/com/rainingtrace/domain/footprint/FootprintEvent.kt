package com.rainingtrace.domain.footprint

import com.rainingtrace.domain.map.HexCellId

/**
 * RT-DOM-006: 足迹事件（append-only 历史）。
 *
 * 足迹不是原始 GPS dump（06_地图专项 §6）：
 * 记录的是"空间 bucket"（HexCellId），不是经纬度。
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
    val cellId: HexCellId,
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
