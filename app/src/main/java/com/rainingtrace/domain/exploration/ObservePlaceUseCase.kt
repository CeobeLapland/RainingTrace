package com.rainingtrace.domain.exploration

import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.inventory.AddItemResult
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import java.util.UUID

/**
 * RT-PLACE-004/005: 地点动作（观察）。
 *
 * 规则：
 * 1. 玩家必须在地点 [OBSERVE_RANGE_METERS] 内（真实亲临，GDD 附录A-4），距离连续、不吸附格子；
 * 2. 地点必须支持 OBSERVE 动作；
 * 3. 冷却：同一地点 [OBSERVE_COOLDOWN_MS] 内只能观察一次。
 *    冷却真相在 footprint 事件表（append-only），重启不重置；
 * 4. 成功 → 发放资源（进库存）+ 写足迹事件。
 *
 * MVP 客户端本地结算；P1 起此逻辑移到服务端（服务器权威）。
 */
class ObservePlaceUseCase(
    private val clock: WorldClock,
    private val inventoryRepository: InventoryRepository,
    private val addItem: AddItemToInventoryUseCase,
    private val footprintRepository: FootprintRepository,
) {

    suspend operator fun invoke(
        playerCoordinate: WorldCoordinate,
        place: Place,
    ): ObserveResult {
        if (PlaceActionType.OBSERVE !in place.actions) {
            return ObserveResult.Rejected(ObserveRejectReason.ACTION_NOT_AVAILABLE)
        }
        val distance = playerCoordinate.distanceMetersTo(place.coordinate)
        if (distance > OBSERVE_RANGE_METERS) {
            return ObserveResult.Rejected(ObserveRejectReason.TOO_FAR)
        }
        val now = clock.now().toEpochMilli()
        if (hasRecentObservation(place.id, now)) {
            return ObserveResult.Rejected(ObserveRejectReason.ON_COOLDOWN)
        }

        val inventory = inventoryRepository.loadState()
        val result = addItem(inventory, REWARD_OBSERVATION_RECORD, 1)
        if (result !is AddItemResult.Success) {
            return ObserveResult.Rejected(ObserveRejectReason.REWARD_FAILED)
        }
        inventoryRepository.saveState(result.state)

        footprintRepository.append(
            FootprintEvent(
                id = UUID.randomUUID().toString(),
                timestampEpochMs = now,
                coordinate = playerCoordinate,
                eventType = FootprintEventType.PLACE_OBSERVED,
                payload = mapOf(
                    "placeId" to place.id,
                    "resourceId" to REWARD_OBSERVATION_RECORD,
                ),
            ),
        )

        return ObserveResult.Success(
            resourceName = "观察记录",
            newQuantity = result.newQuantity,
        )
    }

    private suspend fun hasRecentObservation(placeId: String, nowEpochMs: Long): Boolean =
        footprintRepository.eventsBetween(nowEpochMs - OBSERVE_COOLDOWN_MS, nowEpochMs)
            .any { event ->
                event.eventType == FootprintEventType.PLACE_OBSERVED &&
                    event.payload["placeId"] == placeId
            }

    companion object {
        const val OBSERVE_RANGE_METERS = 120.0
        const val OBSERVE_COOLDOWN_MS = 10 * 60 * 1000L
        const val REWARD_OBSERVATION_RECORD = "res.observation_record"
    }
}

sealed interface ObserveResult {
    data class Success(val resourceName: String, val newQuantity: Int) : ObserveResult
    data class Rejected(val reason: ObserveRejectReason) : ObserveResult
}

enum class ObserveRejectReason {
    TOO_FAR,
    ACTION_NOT_AVAILABLE,
    ON_COOLDOWN,
    REWARD_FAILED,
}
