package com.rainingtrace.domain.inventory

import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.world.ResourceYieldRule
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WorldCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceSourcesTest {

    private val rainy = listOf(WorldCondition.WeatherIn(setOf(WeatherKind.LIGHT_RAIN)))

    private fun rule(
        id: String,
        resourceId: String,
        placeType: PlaceType?,
        conditions: List<WorldCondition> = emptyList(),
    ) = ResourceYieldRule(
        id = id,
        resourceId = resourceId,
        action = PlaceActionType.COLLECT,
        placeType = placeType,
        conditions = conditions,
    )

    @Test
    fun `只取该资源的规则 并保留地点与条件`() {
        val sources = resourceSourcesFor(
            resourceId = "res.rain_moss",
            rules = listOf(
                rule("r1", "res.rain_moss", PlaceType.GARDEN, rainy),
                rule("r2", "res.reed_leaf", PlaceType.LAKE),
            ),
        )

        assertEquals(1, sources.size)
        assertEquals(PlaceType.GARDEN, sources.single().placeType)
        assertEquals(rainy, sources.single().conditions)
    }

    @Test
    fun `地点与条件相同的两条规则只报一次`() {
        val sources = resourceSourcesFor(
            resourceId = "res.rain_moss",
            rules = listOf(
                rule("r1", "res.rain_moss", PlaceType.GARDEN, rainy),
                rule("r2", "res.rain_moss", PlaceType.GARDEN, rainy),
            ),
        )

        assertEquals(1, sources.size)
    }

    @Test
    fun `地点或条件不同算不同来源`() {
        val sources = resourceSourcesFor(
            resourceId = "res.rain_moss",
            rules = listOf(
                rule("r1", "res.rain_moss", PlaceType.GARDEN),
                rule("r2", "res.rain_moss", PlaceType.GARDEN, rainy),
                rule("r3", "res.rain_moss", PlaceType.LAKE),
            ),
        )

        assertEquals(3, sources.size)
    }

    @Test
    fun `没有任何规则时来源为空`() {
        assertTrue(resourceSourcesFor("res.rope", emptyList()).isEmpty())
    }
}