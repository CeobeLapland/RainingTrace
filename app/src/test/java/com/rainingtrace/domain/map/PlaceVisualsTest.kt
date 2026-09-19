package com.rainingtrace.domain.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceVisualsTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)

    private fun place(
        id: String,
        type: PlaceType,
        coordinate: WorldCoordinate = origin,
    ) = Place(
        id = id,
        name = "名字-$id",
        type = type,
        coordinate = coordinate,
        actions = setOf(PlaceActionType.OBSERVE, PlaceActionType.COLLECT),
    )

    private val lake = place("lake", PlaceType.LAKE)
    private val orchard = place("orchard", PlaceType.ORCHARD)

    @Test
    fun `revealed places of shown types become colored markers`() {
        val visuals = placeVisualsFor(
            places = listOf(lake, orchard),
            isRevealed = { true },
            shownTypes = PlaceType.entries.toSet(),
        )

        assertEquals(2, visuals.size)
        assertTrue(visuals.all { it.revealed })
        assertEquals(setOf("名字-lake", "名字-orchard"), visuals.map { it.name }.toSet())
    }

    @Test
    fun `unrevealed human places show as question marks`() {
        val visuals = placeVisualsFor(
            places = listOf(lake),
            isRevealed = { false },
            shownTypes = PlaceType.entries.toSet(),
        )

        assertEquals(1, visuals.size)
        val visual = visuals.single()
        assertTrue(!visual.revealed)
        // 未探索不给名字
        assertEquals("", visual.name)
    }

    @Test
    fun `unrevealed resource nodes are not drawn at all`() {
        val visuals = placeVisualsFor(
            places = listOf(orchard),
            isRevealed = { false },
            shownTypes = PlaceType.entries.toSet(),
        )

        // 给 "?" 等于免费开图：资源点必须自己走近撞见
        assertTrue(visuals.isEmpty())
    }

    @Test
    fun `filtering out a type hides it even when revealed`() {
        val visuals = placeVisualsFor(
            places = listOf(lake, orchard),
            isRevealed = { true },
            shownTypes = setOf(PlaceType.LAKE),
        )

        assertEquals(listOf("名字-lake"), visuals.map { it.name })
    }

    @Test
    fun `every place category has a style`() {
        // 渲染层按类型遍历建层，漏一个类型就会少一个图标层
        PlaceType.entries.forEach { type ->
            val spec = placeStyle(type)
            assertTrue("missing glyph for $type", spec.glyph.isNotBlank())
        }
    }
}