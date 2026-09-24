package com.rainingtrace.domain.inventory

import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType

/**
 * 一种资源"被发现过"的档案：**只增不减**，与当前持有量无关。
 *
 * 图鉴的"已发现"不能用库存数量反推——一旦有消耗（合成扣原料、搬进仓库），
 * 已被采到过的条目会自己变灰，"我明明采到过"被抹掉。真相在 append-only 的
 * 足迹事件里（与 NPC"见过谁"从 `NPC_MET` 派生同构）。
 */
data class ResourceDiscovery(
    val resourceId: String,
    val firstAtEpochMs: Long,
    val lastAtEpochMs: Long,
    /** 获得过多少次：有足迹事件时是成功结算的次数；只靠"当前持有"补进来的资源没有过程记录，取 1 作下限。 */
    val timesAcquired: Int,
) {
    init {
        require(resourceId.isNotBlank()) { "resourceId must not be blank" }
        require(timesAcquired > 0) { "timesAcquired must be positive, got $timesAcquired" }
        require(firstAtEpochMs <= lastAtEpochMs) {
            "firstAt must not be after lastAt: $firstAtEpochMs > $lastAtEpochMs"
        }
    }
}

/** 结算写入的足迹 payload 里，产出资源的那个键（见 `PerformPlaceActionUseCase`）。 */
const val ACQUIRED_RESOURCE_KEY = "resourceId"

/**
 * 从足迹事件 + 当前持有，归并出"发现档案"（纯函数，可单测）。
 *
 * - **事件**：成功结算总会写一条 `PLACE_OBSERVED`，payload 带 `resourceId`。
 * - **当前持有**：制作产出不写足迹，只靠事件会永远判成"未发现"，所以并入
 *   [holdings]（resourceId → 首次获得时刻）补漏；两者取更早的首次时间。
 */
fun resourceDiscoveries(
    events: List<FootprintEvent>,
    holdings: Map<String, Long> = emptyMap(),
): Map<String, ResourceDiscovery> {
    val discovered = mutableMapOf<String, ResourceDiscovery>()

    events.forEach { event ->
        if (event.eventType != FootprintEventType.PLACE_OBSERVED) return@forEach
        val resourceId = event.payload[ACQUIRED_RESOURCE_KEY]
            ?.takeIf { it.isNotBlank() }
            ?: return@forEach
        val existing = discovered[resourceId]
        discovered[resourceId] = if (existing == null) {
            ResourceDiscovery(resourceId, event.timestampEpochMs, event.timestampEpochMs, 1)
        } else {
            existing.copy(
                firstAtEpochMs = minOf(existing.firstAtEpochMs, event.timestampEpochMs),
                lastAtEpochMs = maxOf(existing.lastAtEpochMs, event.timestampEpochMs),
                timesAcquired = existing.timesAcquired + 1,
            )
        }
    }

    holdings.forEach { (resourceId, firstAt) ->
        if (resourceId.isBlank()) return@forEach
        val existing = discovered[resourceId]
        discovered[resourceId] = when {
            existing == null -> ResourceDiscovery(resourceId, firstAt, firstAt, 1)
            firstAt < existing.firstAtEpochMs -> existing.copy(firstAtEpochMs = firstAt)
            else -> existing
        }
    }

    return discovered
}