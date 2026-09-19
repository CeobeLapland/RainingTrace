package com.rainingtrace.domain.exploration

import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.inventory.AddItemResult
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.ResourceCatalog
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.world.ResourceYieldRule
import com.rainingtrace.domain.world.ResourceYieldRuleCatalog
import com.rainingtrace.domain.world.WorldStateProvider
import com.rainingtrace.domain.world.footprintKeys
import java.util.UUID

/**
 * RT-PLACE-004/005: 地点动作（观察）。
 *
 * 规则：
 * 1. 玩家必须在地点 [OBSERVE_RANGE_METERS] 内（真实亲临，GDD 附录A-4），距离连续、不吸附格子；
 * 2. 地点必须支持 OBSERVE 动作；
 * 3. **产出由规则表决定**（[ResourceYieldRuleCatalog]）：取世界状态快照 → 过条件 → 命中即结算。
 *    条件越具体的规则越优先，所以"雨天湖边"会盖过保底的"观察记录"；
 * 4. 冷却按 (地点, 规则) 记，存于 footprint 事件（append-only），重启不重置；
 * 5. 成功 → 发放资源（进库存）+ 写足迹事件（含当时的天气/时段，便于将来做档案）。
 *
 * MVP 客户端本地结算；P1 起此逻辑移到服务端（服务器权威）。
 */
class ObservePlaceUseCase(
    private val clock: WorldClock,
    private val inventoryRepository: InventoryRepository,
    private val addItem: AddItemToInventoryUseCase,
    private val footprintRepository: FootprintRepository,
    private val resourceCatalog: ResourceCatalog,
    private val worldState: WorldStateProvider,
    private val rules: ResourceYieldRuleCatalog,
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

        val state = worldState.current()
        val now = clock.now().toEpochMilli()
        val applicable = rules.rulesFor(PlaceActionType.OBSERVE)
            .filter { it.matches(place, state) }
        val fireable = applicable.filterNot { it.onCooldown(place, now) }
        if (fireable.isEmpty()) {
            // 分得清"这里本来就没东西"和"刚来过、还在冷却"，文案才说得准。
            return ObserveResult.Rejected(
                if (applicable.isEmpty()) {
                    ObserveRejectReason.NOTHING_HERE
                } else {
                    ObserveRejectReason.ON_COOLDOWN
                },
            )
        }
        val rule = fireable.maxByOrNull { it.specificity }!!

        val inventory = inventoryRepository.loadState()
        val result = addItem(inventory, rule.resourceId, rule.amount)
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
                payload = buildMap {
                    put("placeId", place.id)
                    put("ruleId", rule.id)
                    put("resourceId", rule.resourceId)
                    // 当时的天气/时段一起留档：以后做年鉴、时间考古要靠它重建"昨天雨天的湖"。
                    putAll(state.footprintKeys())
                },
            ),
        )

        return ObserveResult.Success(
            resourceId = rule.resourceId,
            resourceName = resourceCatalog.definition(rule.resourceId)?.name ?: rule.resourceId,
            amount = rule.amount,
            newQuantity = result.newQuantity,
        )
    }

    /** 冷却真相在足迹事件里：同一地点 + 同一规则，在各自窗口内只能成功一次。 */
    private suspend fun ResourceYieldRule.onCooldown(place: Place, nowEpochMs: Long): Boolean {
        if (cooldownMs <= 0L) return false
        return footprintRepository.eventsBetween(nowEpochMs - cooldownMs, nowEpochMs)
            .any { event ->
                event.eventType == FootprintEventType.PLACE_OBSERVED &&
                    event.payload["placeId"] == place.id &&
                    event.payload["ruleId"] == id
            }
    }

    companion object {
        const val OBSERVE_RANGE_METERS = 120.0
    }
}

sealed interface ObserveResult {
    data class Success(
        val resourceId: String,
        val resourceName: String,
        /** 本次命中规则发放的数量（规则可以给多份）。 */
        val amount: Int,
        val newQuantity: Int,
    ) : ObserveResult

    data class Rejected(val reason: ObserveRejectReason) : ObserveResult
}

enum class ObserveRejectReason {
    TOO_FAR,
    ACTION_NOT_AVAILABLE,

    /** 动作可用，但当前世界状态下没有任何规则命中（条件都不满足）。 */
    NOTHING_HERE,
    ON_COOLDOWN,
    REWARD_FAILED,
}