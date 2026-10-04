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

    /** 刷出来的资源点：即使类型是"林地"这种 PLACE 类，也不能当人文地点给它画灰问号。 */
    private fun spawnedPlace(type: PlaceType) = Place(
        id = "place.spawn.${type.name.lowercase()}.0",
        name = "刷出来的点",
        type = type,
        coordinate = origin,
        actions = setOf(PlaceActionType.GATHER),
        origin = PlaceOrigin.SPAWNED,
    )

    @Test
    fun `unrevealed spawned points are never drawn even for a human place type`() {
        val visuals = placeVisualsFor(
            places = listOf(spawnedPlace(PlaceType.FOREST)),
            isRevealed = { false },
            shownTypes = PlaceType.entries.toSet(),
        )

        // 只看 category 会把它画成灰 "?"，等于把没走过的地方免费开图
        assertTrue(visuals.isEmpty())
    }

    @Test
    fun `revealed spawned points are drawn like any other place`() {
        val visuals = placeVisualsFor(
            places = listOf(spawnedPlace(PlaceType.FOREST)),
            isRevealed = { true },
            shownTypes = PlaceType.entries.toSet(),
        )

        assertEquals(1, visuals.size)
        assertEquals("刷出来的点", visuals.single().name)
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