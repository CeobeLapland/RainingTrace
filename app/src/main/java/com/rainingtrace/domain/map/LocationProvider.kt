package com.rainingtrace.domain.map

import kotlinx.coroutines.flow.Flow

/** 定位来源：真实 GPS / Fake 调试移动。同时用于轨迹点入库标记。 */
enum class LocationSource { GPS, FAKE }

/**
 * RT-LOC-001: 定位来源接口。
 *
 * 注意：LocationSample 不是游戏事件（06_地图专项 §4）。
 * 这里只输出"原始修正值"，由上层过滤/映射后才产生游戏语义事件。
 */
data class RawLocationFix(
    val coordinate: WorldCoordinate,
    val accuracyMeters: Double,
    val timestampEpochMs: Long,
    val source: LocationSource,
)

interface LocationProvider {
    val updates: Flow<RawLocationFix>

    /** 最近一次有效定位（无则 null）。用于"此刻此地"快照，如拍照建记忆。 */
    val latest: RawLocationFix?
}

/**
 * 采集节奏可调：前台保持高频（地图跟手），进程退到后台时切到用户配置的低频档。
 *
 * 只改采集间隔，不改任何过滤/入库规则；记录用前台服务在进入后台时调它降频，
 * 回前台恢复默认节奏。
 */
interface LocationCadenceController {
    /** [intervalMs] = null 表示恢复前台默认节奏。 */
    fun setPassiveIntervalMs(intervalMs: Long?)
}
