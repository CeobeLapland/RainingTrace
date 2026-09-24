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
     * 规则具体度：条件越多越优先，其次"绑定了地点类型"的优先于泛用的。
     * 加权（条件权重和 ×2 + 地点）让"雨天限定的湖边苔痕"稳稳压过泛用保底规则，
     * 加内容时不需要回头调优先级数值。
     *
     * 用 [WorldCondition.weight] 求和而不是 `conditions.size`：否定条件
     * （`Not`）权重是 0，否则"不下雨"会被当成和"雨天"一样具体。
     */
    val specificity: Int
        get() = conditions.sumOf { it.weight } * 2 + if (placeType != null) 1 else 0

    fun matches(place: Place, state: WorldState): Boolean {
        if (placeType != null && place.type != placeType) return false
        if (action !in place.actions) return false
        return conditions.allSatisfiedBy(state)
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 10 * 60 * 1000L
    }
}

/** 产出规则目录：运行时由 `ContentYieldRuleCatalog` 读内容索引；这个实现留给测试。 */
interface ResourceYieldRuleCatalog {
    fun rulesFor(action: PlaceActionType): List<ResourceYieldRule>

    /** 全部规则；给"按 resourceId 反查来源"这类用途（图鉴的"来源"）。 */
    fun all(): List<ResourceYieldRule>
}

/**
 * 内存实现：规则全部来自构造参数。
 *
 * 内容本体住在 `assets/content/yield_rules.json`（+ 私有目录覆盖层），
 * 所以这里不再内置任何一条规则。
 */
class InMemoryResourceYieldRuleCatalog(
    private val rules: List<ResourceYieldRule>,
) : ResourceYieldRuleCatalog {

    private val byAction: Map<PlaceActionType, List<ResourceYieldRule>> = rules.groupBy { it.action }

    override fun rulesFor(action: PlaceActionType): List<ResourceYieldRule> =
        byAction[action].orEmpty()

    override fun all(): List<ResourceYieldRule> = rules
}