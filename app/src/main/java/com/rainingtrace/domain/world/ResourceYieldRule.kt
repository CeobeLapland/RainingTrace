package com.rainingtrace.domain.world

import com.rainingtrace.domain.inventory.InMemoryResourceCatalog
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType

/**
 * 地点动作的产出规则：**世界状态 → 产出**的唯一通道（GDD §07/§09）。
 *
 * 之前产出硬编码在用例里（观察必得"观察记录"），所以"雨天湖边掉湖泊记忆碎片"
 * 这类内容没有可写的位置。现在一律走这张表：用例只负责
 * 「取世界状态快照 → 过规则 → 结算」，加内容只加规则、不动逻辑。
 *
 * 与 GDD §21 对齐：规则是纯数据（条件可枚举、可序列化），
 * 将来搬进开发者编辑器即可，无需改核心代码。
 */
data class ResourceYieldRule(
    val id: String,
    val resourceId: String,
    val action: PlaceActionType,
    /** null = 任意地点类型。 */
    val placeType: PlaceType? = null,
    /** 全部满足才出这个产出；空 = 无条件（保底规则）。 */
    val conditions: List<WorldCondition> = emptyList(),
    val amount: Int = 1,
    /** 同一地点同一规则的重试间隔；每条规则各自冷却，罕见机会不被保底规则挡住。 */
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    init {
        require(id.isNotBlank()) { "rule id must not be blank" }
        require(resourceId.isNotBlank()) { "resourceId must not be blank" }
        require(amount > 0) { "amount must be positive, got $amount" }
        require(cooldownMs >= 0) { "cooldown must not be negative, got $cooldownMs" }
    }

    /**
     * 越"具体"（条件越多）的规则越优先：雨天湖边的碎片先于泛泛的观察记录。
     * 这样加条件内容不需要调优先级数值。
     */
    val specificity: Int get() = conditions.size

    fun matches(place: Place, state: WorldState): Boolean {
        if (placeType != null && place.type != placeType) return false
        if (action !in place.actions) return false
        return conditions.allSatisfiedBy(state)
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 10 * 60 * 1000L
    }
}

/** 产出规则目录：Fake/内存实现，P1 起随内容一起由服务端下发。 */
interface ResourceYieldRuleCatalog {
    fun rulesFor(action: PlaceActionType): List<ResourceYieldRule>
}

class InMemoryResourceYieldRuleCatalog(
    rules: List<ResourceYieldRule>,
) : ResourceYieldRuleCatalog {

    private val byAction: Map<PlaceActionType, List<ResourceYieldRule>> = rules.groupBy { it.action }

    override fun rulesFor(action: PlaceActionType): List<ResourceYieldRule> =
        byAction[action].orEmpty()

    companion object {
        /** 保底：任何地点认真看一次 → 观察记录（MVP 原有行为）。 */
        val OBSERVE_BASE = ResourceYieldRule(
            id = "rule.observe.base",
            resourceId = InMemoryResourceCatalog.OBSERVATION_RECORD.id,
            action = PlaceActionType.OBSERVE,
        )

        /**
         * 第一个"世界状态影响产出"的例子（GDD §26 样例、11_MVP §3 的"雨后水面异常反光"）：
         * 雨天的湖边观察 → 湖泊记忆碎片。冷却比保底长，避免反复刷。
         */
        val OBSERVE_RAINY_LAKE = ResourceYieldRule(
            id = "rule.observe.rainy_lake",
            resourceId = InMemoryResourceCatalog.LAKE_MEMORY_FRAGMENT.id,
            action = PlaceActionType.OBSERVE,
            placeType = PlaceType.LAKE,
            conditions = listOf(RAINY_WEATHER),
            cooldownMs = 30 * 60 * 1000L,
        )

        val DEFAULT = listOf(OBSERVE_BASE, OBSERVE_RAINY_LAKE)
    }
}