package com.rainingtrace.domain.settings

import com.rainingtrace.domain.map.PlaceCategory
import com.rainingtrace.domain.map.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFilterSettingsTest {

    @Test
    fun `nothing hidden means everything is shown`() {
        assertEquals(
            PlaceType.entries.toSet(),
            shownPlaceTypesFrom(emptySet()),
        )
    }

    @Test
    fun `hidden types are excluded`() {
        val shown = shownPlaceTypesFrom(setOf(PlaceType.LAKE, PlaceType.ORCHARD))

        assertFalse(PlaceType.LAKE in shown)
        assertFalse(PlaceType.ORCHARD in shown)
        assertTrue(PlaceType.LIBRARY in shown)
    }

    @Test
    fun `a newly added type is visible by default`() {
        // 老安装的隐藏集合里当然不会有新类型 → 新类型自动可见。
        // 这正是"存隐藏集合而不是显示集合"要解决的问题。
        val hiddenSavedLongAgo = setOf(PlaceType.LAKE)
        val shown = shownPlaceTypesFrom(hiddenSavedLongAgo)

        assertTrue(PlaceType.MUSHROOM_PATCH in shown)
        assertTrue(PlaceType.ORCHARD in shown)
        assertTrue(PlaceType.BERRY_BUSH in shown)
    }

    @Test
    fun `default settings show every type`() {
        assertEquals(PlaceType.entries.toSet(), MapFilterSettings().shownPlaceTypes)
    }

    @Test
    fun `resource node types are grouped as resources`() {
        val resources = PlaceType.entries.filter { it.category == PlaceCategory.RESOURCE }
        val places = PlaceType.entries.filter { it.category == PlaceCategory.PLACE }

        assertEquals(
            setOf(PlaceType.ORCHARD, PlaceType.BERRY_BUSH, PlaceType.MUSHROOM_PATCH),
            resources.toSet(),
        )
        assertTrue(places.isNotEmpty())
        // 两组必须覆盖全部类型，否则面板上会有类型"看不见"
        assertEquals(PlaceType.entries.size, resources.size + places.size)
    }
}