package com.rainingtrace.domain.spawn

import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.WorldCondition

/**
 * 一个候选点：实际生成位置在该点 [radiusMeters] 内确定性偏移。
 *
 * 位置**只能来自人给的候选点**，所以"大马路上刷苹果树"在口径上就被排除了——
 * 随机只作用在"候选点里今天激活哪几个"和"点内偏一点"，不会凭空落在任意格子上。
 */
data class SpawnSpot(
    val coordinate: WorldCoordinate,
    val radiusMeters: Double,
) {
    init {
        require(radiusMeters > 0.0) { "spot radius must be positive, got $radiusMeters" }
    }
}

/**
 * 资源点刷新规则（`spawn_rules.json`）。
 *
 * 一条规则说清四件事：**刷什么**（[placeType]/[name]）、**刷在哪**（[spots]）、
 * **刷几个**（[perDay]）、**什么时候算有**（[conditions]）。产出不在这里配——
 * 刷出来的点照样吃 `yield_rules.json`（按 [placeType] + 动作匹配）。
 */
data class SpawnRule(
    val id: String,
    val name: String,
    val placeType: PlaceType,
    val spots: List<SpawnSpot>,
    /** 今天在候选点里激活几个（实际生效时夹到 `spots.size` 以内）。 */
    val perDay: Int,
    val description: String = "",
    /**
     * 可执行动作；`null` = 用 `defaultActionsFor(placeType)`，
     * 与"现场记点"同一口径，所以这里不用重复维护一份。
     */
    val actions: Set<PlaceActionType>? = null,
    /** 出现后存在多久（分钟）。默认整天：不写就是"当天一直在"。 */
    val lifetimeMinutes: Int = MINUTES_PER_DAY,
    /** 空 = 无条件，恒可出现。 */
    val conditions: List<WorldCondition> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "spawn rule id must not be blank" }
        require(name.isNotBlank()) { "spawn rule name must not be blank: $id" }
        require(spots.isNotEmpty()) { "spawn rule needs at least one spot: $id" }
        require(perDay >= 1) { "perDay must be at least 1: $id" }
        require(lifetimeMinutes in 1..MINUTES_PER_DAY) {
            "lifetimeMinutes must be 1..$MINUTES_PER_DAY: $id"
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** 刷新规则表读口（内容层提供实现，与 `ResourceYieldRuleCatalog` 同构）。 */
interface SpawnRuleCatalog {
    fun rules(): List<SpawnRule>
}