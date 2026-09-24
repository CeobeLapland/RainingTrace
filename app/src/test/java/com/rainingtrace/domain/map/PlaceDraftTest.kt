package com.rainingtrace.domain.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceDraftTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)

    private fun draft(
        name: String = "某地",
        type: PlaceType = PlaceType.OTHER,
        actions: Set<PlaceActionType> = defaultActionsFor(type),
    ) = PlaceDraft(name = name, type = type, coordinate = origin, actions = actions)

    @Test
    fun `名字为空时给出可读的原因`() {
        assertEquals("还没给它起名字", draft(name = "   ").rejectionReason())
    }

    @Test
    fun `没有动作时拒绝`() {
        val reason = draft(actions = emptySet()).rejectionReason()

        assertTrue(reason != null && reason.contains("动作"))
    }

    @Test
    fun `正常草稿可以落盘`() {
        assertNull(draft().rejectionReason())
    }

    @Test
    fun `人文地点默认可观察可采集，资源点默认只采集`() {
        assertEquals(
            setOf(PlaceActionType.OBSERVE, PlaceActionType.COLLECT),
            defaultActionsFor(PlaceType.LAKE),
        )
        assertEquals(setOf(PlaceActionType.COLLECT), defaultActionsFor(PlaceType.ORCHARD))
    }

    @Test
    fun `新 id 用 user 前缀，和内置内容分开`() {
        val id = newPlaceId(nowMs = 1_700_000_000_000, taken = emptySet())

        assertEquals("place.user.1700000000000", id)
        assertFalse(id.startsWith("place.bit."))
    }

    @Test
    fun `同一毫秒重复提交时 id 加序号而不是撞车`() {
        val first = newPlaceId(nowMs = 42, taken = emptySet())
        val second = newPlaceId(nowMs = 42, taken = setOf(first))
        val third = newPlaceId(nowMs = 42, taken = setOf(first, second))

        assertEquals("place.user.42", first)
        assertEquals("place.user.42-2", second)
        assertEquals("place.user.42-3", third)
    }
}