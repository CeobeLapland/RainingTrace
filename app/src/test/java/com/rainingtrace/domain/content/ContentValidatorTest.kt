package com.rainingtrace.domain.content

import com.rainingtrace.domain.inventory.Rarity
import com.rainingtrace.domain.inventory.ResourceCategory
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.ResourceYieldRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentValidatorTest {

    private fun place(id: String) = Place(
        id = id,
        name = "地点",
        type = PlaceType.LAKE,
        coordinate = WorldCoordinate(39.7326, 116.1712),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    @Test
    fun `正常内容没有诊断`() {
        val report = ContentReport()
        ContentValidator.validate(WorldContent(places = listOf(place("a"), place("b"))), report)

        assertEquals(0, report.items.size)
    }

    @Test
    fun `id 重复会被报错`() {
        val report = ContentReport()
        ContentValidator.validate(WorldContent(places = listOf(place("a"), place("a"))), report)

        assertEquals(1, report.errorCount)
    }

    @Test
    fun `空内容没有诊断`() {
        val report = ContentReport()
        ContentValidator.validate(WorldContent(), report)

        assertEquals(0, report.items.size)
    }

    @Test
    fun `产出规则引用的资源不存在时丢掉那条规则`() {
        val report = ContentReport()
        val content = WorldContent(
            resources = listOf(resource("res.known")),
            yieldRules = listOf(
                rule("rule.ok", "res.known"),
                rule("rule.dangling", "res.missing"),
            ),
        )

        val cleaned = ContentValidator.validate(content, report)

        assertEquals(listOf("rule.ok"), cleaned.yieldRules.map { it.id })
        assertEquals(1, report.errorCount)
        assertTrue(report.items.first().entryId == "rule.dangling")
    }

    private fun resource(id: String) = ResourceDefinition(
        id = id,
        name = "资源",
        category = ResourceCategory.NATURE,
        rarity = Rarity.COMMON,
    )

    private fun rule(id: String, resourceId: String) = ResourceYieldRule(
        id = id,
        resourceId = resourceId,
        action = PlaceActionType.COLLECT,
    )
}