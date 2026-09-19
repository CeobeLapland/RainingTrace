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
 * RT-PLACE-004/005: 地点动作（观察 / 采集）。
 *
 * **一个动作一个入口**：观察和采集的前置校验、结算、冷却、留档只有这一份实现，
 * 差异全部交给规则表（[ResourceYieldRule] 按 [PlaceActionType] 分组）。
 * 再加"钓鱼/交易/休息"时只需加动作类型 + 规则，不用复制这段逻辑。
 *
 * 规则：
 * 1. 玩家必须在地点 [rangeMetersFor] 内（真实亲临，GDD 附录A-4），距离连续、不吸附格子；
 * 2. 地点必须支持该动作；
 * 3. **产出由规则表决定**：取世界状态快照 → 过条件 → 命中即结算；
 *    条件越具体的规则越优先，所以"雨夜湖边的鱼影"盖过"雨天碎片"盖过保底；
 * 4. 冷却按 (地点, 规则) 记，存于 footprint 事件（append-only），重启不重置；
 * 5. 成功 → 发放资源（进库存）+ 写足迹事件（含当时的天气/时段/季节，供将来做档案）。
 *
 * MVP 客户端本地结算；P1 起此逻辑移到服务端（服务器权威）。
 */
class PerformPlaceActionUseCase(
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
        action: PlaceActionType,
    ): PlaceActionResult {
        val distance = playerCoordinate.distanceMetersTo(place.coordinate)
        if (distance > rangeMetersFor(action)) {
            return PlaceActionResult.Rejected(PlaceActionRejectReason.TOO_FAR)
        }

        val now = clock.now().toEpochMilli()
        val selection = selectRule(place, action, now)
        if (selection is Selection.Miss) {
            return PlaceActionResult.Rejected(selection.reason)
        }
        val rule = (selection as Selection.Hit).rule

        val inventory = inventoryRepository.loadState()
        val result = addItem(inventory, rule.resourceId, rule.amount)
        if (result !is AddItemResult.Success) {
            return PlaceActionResult.Rejected(PlaceActionRejectReason.REWARD_FAILED)
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
                    put("action", action.name)
                    put("ruleId", rule.id)
                    put("resourceId", rule.resourceId)
                    // 当时的世界状态一起留档：以后做年鉴、时间考古要靠它重建"昨天雨天的湖"。
                    putAll(worldState.current().footprintKeys())
                },
            ),
        )

        return PlaceActionResult.Success(
            action = action,
            resourceId = rule.resourceId,
            resourceName = resourceNameOf(rule.resourceId),
            amount = rule.amount,
            newQuantity = result.newQuantity,
        )
    }

    /**
     * 只读预览：此刻在这个地点做这个动作会得到什么。
     *
     * 与 [invoke] 共用同一套 [selectRule]，所以预览不会和实际结算漂移；
     * 不扣冷却、不写足迹、不动库存（冷却用的是已发生过的足迹事件，天然只读）。
     * 故意不含距离判断：那是玩家站位问题，由地点卡自己显示距离。
     */
    suspend fun preview(place: Place, action: PlaceActionType): PlaceYieldPreview {
        val selection = selectRule(place, action, clock.now().toEpochMilli())
        return when (selection) {
            is Selection.Miss -> PlaceYieldPreview.Unavailable(selection.reason)
            is Selection.Hit -> PlaceYieldPreview.Ready(
                action = action,
                resourceId = selection.rule.resourceId,
                resourceName = resourceNameOf(selection.rule.resourceId),
                amount = selection.rule.amount,
            )
        }
    }

    /**
     * 选规则：动作可用 → 条件命中 → 未被自己的冷却挡住 → 取最具体的一条。
     * 观察与采集共用这一处，预览与实际结算也共用。
     */
    private suspend fun selectRule(
        place: Place,
        action: PlaceActionType,
        nowEpochMs: Long,
    ): Selection {
        if (action !in place.actions) {
            return Selection.Miss(PlaceActionRejectReason.ACTION_NOT_AVAILABLE)
        }
        val state = worldState.current()
        val applicable = rules.rulesFor(action).filter { it.matches(place, state) }
        val fireable = applicable.filterNot { it.onCooldown(place, nowEpochMs) }
        if (fireable.isEmpty()) {
            // 分得清"这里本来就没东西"和"刚来过、还在冷却"，文案才说得准。
            return Selection.Miss(
                if (applicable.isEmpty()) {
                    PlaceActionRejectReason.NOTHING_HERE
                } else {
                    PlaceActionRejectReason.ON_COOLDOWN
                },
            )
        }
        return Selection.Hit(fireable.chooseMostSpecific())
    }

    private fun resourceNameOf(resourceId: String): String =
        resourceCatalog.definition(resourceId)?.name ?: resourceId

    private sealed interface Selection {
        data class Hit(val rule: ResourceYieldRule) : Selection
        data class Miss(val reason: PlaceActionRejectReason) : Selection
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

        /** 采集要更近一些：走到跟前才算"采"，观察可以站远点看。 */
        const val COLLECT_RANGE_METERS = 60.0

        fun rangeMetersFor(action: PlaceActionType): Double = when (action) {
            PlaceActionType.OBSERVE -> OBSERVE_RANGE_METERS
            PlaceActionType.COLLECT -> COLLECT_RANGE_METERS
        }
    }
}

/**
 * 具体度最高的规则优先；并列时按 id 取字典序最后者。
 *
 * 并列是内容设计的信号（同一条件组合下不该有两条同权规则），
 * 但行为必须确定，不能依赖集合顺序，所以这里显式定死。
 */
internal fun List<ResourceYieldRule>.chooseMostSpecific(): ResourceYieldRule =
    maxWith(compareBy({ it.specificity }, { it.id }))

sealed interface PlaceActionResult {
    data class Success(
        val action: PlaceActionType,
        val resourceId: String,
        val resourceName: String,
        /** 本次命中规则发放的数量（规则可以给多份）。 */
        val amount: Int,
        val newQuantity: Int,
    ) : PlaceActionResult

    data class Rejected(val reason: PlaceActionRejectReason) : PlaceActionResult
}

/**
 * 动作的"此刻产出"预览（地点卡上的提示）。
 * 与实际结算共用选规则逻辑，所以只要它显示有产出，点下去就能拿到。
 */
sealed interface PlaceYieldPreview {
    data class Ready(
        val action: PlaceActionType,
        val resourceId: String,
        val resourceName: String,
        val amount: Int,
    ) : PlaceYieldPreview

    data class Unavailable(val reason: PlaceActionRejectReason) : PlaceYieldPreview
}

enum class PlaceActionRejectReason {
    TOO_FAR,
    ACTION_NOT_AVAILABLE,

    /** 动作可用，但当前世界状态下没有任何规则命中（条件都不满足）。 */
    NOTHING_HERE,
    ON_COOLDOWN,
    REWARD_FAILED,
}