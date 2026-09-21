package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcScheduleTest {

    private val places = mapOf(
        "a" to place("a", 116.0),
        "b" to place("b", 117.0),
    )

    private fun place(id: String, lng: Double) = Place(
        id = id,
        name = "地点$id",
        type = PlaceType.OTHER,
        coordinate = WorldCoordinate(39.0, lng),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private fun scheduleOf(vararg entries: NpcScheduleEntry) = NpcProfile(
        id = "npc.test",
        name = "测试",
        oneLiner = "",
        schedule = entries.toList(),
    )

    @Test
    fun `stays at the entry place inside its interval`() {
        val schedule = resolveSchedule(
            scheduleOf(
                NpcScheduleEntry(startMinute = 600, placeId = "a"),
                NpcScheduleEntry(startMinute = 1200, placeId = "b", travelMinutes = 30),
            ),
            places,
        )

        val presence = schedule.presenceAt(800)!!

        assertFalse(presence.walking)
        assertEquals("a", presence.placeId)
        assertEquals(116.0, presence.coordinate.lngDegrees, 1e-9)
    }

    @Test
    fun `interpolates linearly on the way to the next place`() {
        val schedule = resolveSchedule(
            scheduleOf(
                NpcScheduleEntry(startMinute = 600, placeId = "a"),
                NpcScheduleEntry(startMinute = 1200, placeId = "b", travelMinutes = 30),
            ),
            places,
        )

        // 到达前 15 分钟：正好走了一半
        val presence = schedule.presenceAt(1185)!!

        assertTrue(presence.walking)
        assertEquals(0.5, presence.progress, 1e-9)
        assertEquals(116.5, presence.coordinate.lngDegrees, 1e-9)
        // 走路时报的是目的地
        assertEquals("b", presence.placeId)
    }

    @Test
    fun `the last entry walks to the first entry of the next day`() {
        val schedule = resolveSchedule(
            scheduleOf(
                NpcScheduleEntry(startMinute = 600, placeId = "a", travelMinutes = 30),
                NpcScheduleEntry(startMinute = 1200, placeId = "b"),
            ),
            places,
        )

        // 09:50 正走向 a（10:00 到达）：这一段是从"昨晚 20:00 在 b"延续过来的
        val presence = schedule.presenceAt(590)!!

        assertTrue(presence.walking)
        assertEquals("a", presence.placeId)
        assertEquals(1.0 - 10.0 / 30.0, presence.progress, 1e-9)
    }

    @Test
    fun `walking is detected after midnight`() {
        val schedule = resolveSchedule(
            scheduleOf(
                NpcScheduleEntry(startMinute = 60, placeId = "a", travelMinutes = 40),
                NpcScheduleEntry(startMinute = 600, placeId = "b"),
            ),
            places,
        )

        // 00:30，40 分钟后（01:00）到达 a
        val presence = schedule.presenceAt(30)!!

        assertTrue(presence.walking)
        assertEquals("a", presence.placeId)
        assertEquals(1.0 - 30.0 / 40.0, presence.progress, 1e-9)
    }

    @Test
    fun `a single entry never walks`() {
        val schedule = resolveSchedule(
            scheduleOf(NpcScheduleEntry(startMinute = 300, placeId = "a", travelMinutes = 120)),
            places,
        )

        assertFalse(schedule.presenceAt(0)!!.walking)
        assertFalse(schedule.presenceAt(300)!!.walking)
        assertFalse(schedule.presenceAt(1000)!!.walking)
        assertEquals("a", schedule.presenceAt(1000)!!.placeId)
    }

    @Test
    fun `an empty schedule has no presence`() {
        assertNull(resolveSchedule(scheduleOf(), places).presenceAt(600))
    }

    @Test
    fun `entries pointing at unknown places are dropped`() {
        val schedule = resolveSchedule(
            scheduleOf(
                NpcScheduleEntry(startMinute = 600, placeId = "a"),
                NpcScheduleEntry(startMinute = 700, placeId = "missing"),
            ),
            places,
        )

        assertEquals("a", schedule.presenceAt(650)!!.placeId)
        assertNull(resolveSchedule(scheduleOf(NpcScheduleEntry(600, "missing")), places).presenceAt(600))
    }

    @Test
    fun `travel longer than the gap is clamped so the stay is preserved`() {
        val resolved = resolveSchedule(
            scheduleOf(
                NpcScheduleEntry(startMinute = 600, placeId = "a", travelMinutes = 30),
                NpcScheduleEntry(startMinute = 610, placeId = "b", travelMinutes = 999),
            ),
            places,
        )

        // 间隔只有 10 分钟 → 行走夹到 9 分钟，留 1 分钟停在 a
        assertFalse(resolved.presenceAt(600)!!.walking)
        assertEquals("a", resolved.presenceAt(600)!!.placeId)
        assertTrue(resolved.presenceAt(605)!!.walking)
    }

    @Test
    fun `adjacent entries at the same place never walk`() {
        val resolved = resolveSchedule(
            scheduleOf(
                NpcScheduleEntry(startMinute = 600, placeId = "a", travelMinutes = 30),
                NpcScheduleEntry(startMinute = 700, placeId = "a", travelMinutes = 30),
            ),
            places,
        )

        assertFalse(resolved.presenceAt(690)!!.walking)
        assertEquals("a", resolved.presenceAt(690)!!.placeId)
    }
}
