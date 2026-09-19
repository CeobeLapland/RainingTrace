package com.rainingtrace.domain.world

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
    private val catalog = InMemoryResourceYieldRuleCatalog(InMemoryResourceYieldRuleCatalog.DEFAULT)

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
        val rule = InMemoryResourceYieldRuleCatalog.OBSERVE_BASE

        assertTrue(rule.matches(place(PlaceType.LAKE), state(12, WeatherKind.CLEAR)))
        assertTrue(rule.matches(place(PlaceType.LIBRARY), state(12, WeatherKind.SNOW)))
        assertEquals(0, rule.specificity)
    }

    @Test
    fun `conditional rule matches only its place type and weather`() {
        val rule = InMemoryResourceYieldRuleCatalog.OBSERVE_RAINY_LAKE

        assertTrue(rule.matches(place(PlaceType.LAKE), state(12, WeatherKind.LIGHT_RAIN)))
        // 天气对但地点不对
        assertFalse(rule.matches(place(PlaceType.LIBRARY), state(12, WeatherKind.LIGHT_RAIN)))
        // 地点对但天气不对
        assertFalse(rule.matches(place(PlaceType.LAKE), state(12, WeatherKind.CLEAR)))
    }

    @Test
    fun `conditional rule is more specific than the base rule`() {
        val base = InMemoryResourceYieldRuleCatalog.OBSERVE_BASE
        val conditional = InMemoryResourceYieldRuleCatalog.OBSERVE_RAINY_LAKE

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

    @Test
    fun `every default rule yields a resource that exists in the catalog`() {
        val resources = InMemoryResourceCatalog(InMemoryResourceCatalog.DEFAULT)

        InMemoryResourceYieldRuleCatalog.DEFAULT.forEach { rule ->
            assertTrue(
                "missing resource definition for ${rule.resourceId}",
                resources.definition(rule.resourceId) != null,
            )
        }
    }

    @Test
    fun `default catalog keeps the conditional rule's cooldown longer than the base`() {
        val base = InMemoryResourceYieldRuleCatalog.OBSERVE_BASE
        val conditional = InMemoryResourceYieldRuleCatalog.OBSERVE_RAINY_LAKE

        assertTrue(conditional.cooldownMs > base.cooldownMs)
    }
}