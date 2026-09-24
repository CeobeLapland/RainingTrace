package com.rainingtrace.domain.inventory

import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository

/**
 * 读"资源发现档案"：真相在 append-only 的足迹事件里，再并入当前持有补漏。
 *
 * **按需调用**（图鉴页签打开时），刻意不做订阅：每次采集都会写足迹，
 * 挂在库存流上会变成"每次采集都全量重算"。
 */
class LoadResourceDiscoveriesUseCase(
    private val footprintRepository: FootprintRepository,
    private val inventoryRepository: InventoryRepository,
    /** 仓库（可选）：搬进仓库的东西依然算"发现过"，不因为不在背包里就失效。 */
    private val warehouseRepository: InventoryRepository? = null,
) {

    suspend operator fun invoke(): Map<String, ResourceDiscovery> {
        val holdings = buildMap {
            putAll(inventoryRepository.loadState().firstAcquiredTimes())
            warehouseRepository?.let { putAll(it.loadState().firstAcquiredTimes()) }
        }
        return resourceDiscoveries(
            events = footprintRepository.eventsOfType(FootprintEventType.PLACE_OBSERVED),
            holdings = holdings,
        )
    }

    private fun InventoryState.firstAcquiredTimes(): Map<String, Long> =
        items.mapValues { (_, item) -> item.firstAcquiredAtEpochMs }
}