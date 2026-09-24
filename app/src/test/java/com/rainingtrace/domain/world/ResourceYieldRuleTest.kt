package com.rainingtrace.domain.world

import com.rainingtrace.data.content.ShippedContent
import com.rainingtrace.domain.inventory.InMemoryResourceCatalog
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceYieldRuleTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)
    private val catalog = InMemoryResourceYieldRuleCatalog(ShippedContent.yieldRules)

    private fun place(type: PlaceType, id: String = "place.x") = Place(
        id = id,
        name = "地点",
        type = type,
        coordinate = origin,
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private fun state(hour: Int, kind: WeatherKind) = deriveWorldState(
        instant = LocalDate.of(2026, 9, 19).atTime(hour, 0).toInstant(ZoneOffset.ofHours(8)),
        weather = WeatherState(kind),
    )

    @Test
    fun `base rule matches any place when there is no condition`() {
        val rule = ShippedContent.rule("rule.observe.base")

        assertTrue(rule.matches(place(PlaceType.LAKE), state(12, WeatherKind.CLEAR)))
        assertTrue(rule.matches(place(PlaceType.LIBRARY), state(12, WeatherKind.SNOW)))
        assertEquals(0, rule.specificity)
    }

    /**
     * 否定条件（`Not`）说的是"不要什么"，比正向条件更不具体。
     * 如果按条件条数算具体度，"不下雨的湖边"会压过"雨天的湖边"——方向就反了。
     */
    @Test
    fun `a negated condition does not outrank the positive one`() {
        val rainy = ResourceYieldRule(
            id = "rule.rainy",
            resourceId = "res.x",
            action = PlaceActionType.OBSERVE,
            placeType = PlaceType.LAKE,
            conditions = listOf(RAINY_WEATHER),
        )
        val notRainy = ResourceYieldRule(
            id = "rule.not_rainy",
            resourceId = "res.x",
            action = PlaceActionType.OBSERVE,
            placeType = PlaceType.LAKE,
            conditions = listOf(WorldCondition.Not(RAINY_WEATHER)),
        )

        assertTrue(notRainy.specificity < rainy.specificity)
        // "不下雨的湖边"确实在晴天成立 —— 语义和第二半句一致。
        assertTrue(notRainy.matches(place(PlaceType.LAKE), state(12, WeatherKind.CLEAR)))
    }

    @Test
    fun `conditional rule matches only its place type and weather`() {
        val rule = ShippedContent.rule("rule.observe.rainy_lake")

        assertTrue(rule.matches(place(PlaceType.LAKE), state(12, WeatherKind.LIGHT_RAIN)))
        // 天气对但地点不对
        assertFalse(rule.matches(place(PlaceType.LIBRARY), state(12, WeatherKind.LIGHT_RAIN)))
        // 地点对但天气不对
        assertFalse(rule.matches(place(PlaceType.LAKE), state(12, WeatherKind.CLEAR)))
    }

    @Test
    fun `conditional rule is more specific than the base rule`() {
        val base = ShippedContent.rule("rule.observe.base")
        val conditional = ShippedContent.rule("rule.observe.rainy_lake")

        assertTrue(conditional.specificity > base.specificity)
    }

    @Test
    fun `catalog groups rules by action`() {
        val observeRules = catalog.rulesFor(PlaceActionType.OBSERVE)
        val collectRules = catalog.rulesFor(PlaceActionType.COLLECT)

        // 两个动作各自有规则，且互不串场
        assertTrue(observeRules.isNotEmpty())
        assertTrue(collectRules.isNotEmpty())
        assertTrue(observeRules.all { it.action == PlaceActionType.OBSERVE })
        assertTrue(collectRules.all { it.action == PlaceActionType.COLLECT })
        assertEquals(
            emptyList<ResourceYieldRule>(),
            InMemoryResourceYieldRuleCatalog(emptyList()).rulesFor(PlaceActionType.OBSERVE),
        )
    }

    /**
     * 引用完整性：规则指向的资源必须存在。
     * 这条原来靠编译期常量，现在靠真实 JSON —— `ShippedContent` 读的就是内置文件。
     */
    @Test
    fun `every shipped rule yields a resource that exists in the catalog`() {
        val resources = InMemoryResourceCatalog(ShippedContent.resources)

        ShippedContent.yieldRules.forEach { rule ->
            assertTrue(
                "missing resource definition for ${rule.resourceId}",
                resources.definition(rule.resourceId) != null,
            )
        }
    }

    @Test
    fun `default catalog keeps the conditional rule's cooldown longer than the base`() {
        val base = ShippedContent.rule("rule.observe.base")
        val conditional = ShippedContent.rule("rule.observe.rainy_lake")

        assertTrue(conditional.cooldownMs > base.cooldownMs)
    }
}