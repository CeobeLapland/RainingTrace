package com.rainingtrace.domain.inventory

import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.world.ResourceYieldRule
import com.rainingtrace.domain.world.WorldCondition

/**
 * 一种资源"从哪来"的**结构化**描述：地点类型 + 需要满足的世界条件。
 *
 * 刻意不带中文文案——domain 不产出面向玩家的字串（与 `PlaceActionRejectReason`
 * 同口径：领域给结构，feature 给文案）。渲染见 `core/ui` 的
 * `PlaceType.label()` / `WorldCondition.describe()`。
 *
 * 只描述**地点动作**里的来源（产出规则）；配方产出不在其中——
 * 那是"加工"，图鉴里会单独说出来源类型。
 */
data class ResourceSource(
    val placeType: PlaceType?,
    val conditions: List<WorldCondition>,
)

/**
 * 反查一种资源的所有地点来源。
 *
 * 产出规则本来就带着 `resourceId` 与条件，所以图鉴的"来源"不需要任何新字段，
 * 把规则反查一遍即可。
 */
fun resourceSourcesFor(
    resourceId: String,
    rules: List<ResourceYieldRule>,
): List<ResourceSource> =
    rules.asSequence()
        .filter { it.resourceId == resourceId }
        .map { ResourceSource(it.placeType, it.conditions) }
        .distinct()
        .toList()