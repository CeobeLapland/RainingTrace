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

class ScheduleFactsTest {

    private fun place(id: String, name: String) = Place(
        id = id,
        name = name,
        type = PlaceType.OTHER,
        coordinate = WorldCoordinate(39.0, 116.0),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private val placesById = listOf(
        place("a", "图书馆"),
        place("b", "学生宿舍 3 号楼"),
    ).associateBy { it.id }

    /** 10:00 在图书馆；20:00 到达宿舍，路上 30 分钟（19:30-20:00 在路上）。 */
    private val profile = NpcProfile(
        id = "npc.test",
        name = "测试",
        oneLiner = "",
        schedule = listOf(
            NpcScheduleEntry(startMinute = 600, placeId = "a", activity = "在靠窗的位置自习"),
            NpcScheduleEntry(startMinute = 1200, placeId = "b", travelMinutes = 30, activity = "回宿舍"),
        ),
    )

    @Test
    fun `tomorrow afternoon maps to the fixed minute and reads the real schedule`() {
        val facts = scheduleFacts(
            profile = profile,
            places = placesById,
            timeHint = TimeHint.TOMORROW_AFTERNOON,
            nowMinuteOfDay = 600,
        )!!

        // 时间提示换成固定时刻 14:00；地点来自真实作息
        assertEquals(840, facts.targetMinuteOfDay)
        assertEquals("明天下午", facts.timeLabel)
        assertEquals(resolveSchedule(profile, placesById).presenceAt(840)!!.placeName, facts.placeName)
        assertFalse(facts.walking)
    }

    @Test
    fun `today and tomorrow afternoon give the same answer`() {
        val fromToday = scheduleFacts(profile, placesById, TimeHint.TOMORROW_AFTERNOON, 840)!!
        val fromMorning = scheduleFacts(profile, placesById, TimeHint.TOMORROW_AFTERNOON, 600)!!

        // 作息是日环：不因为"现在是几点"而改变"明天下午在哪"
        assertEquals(fromToday.targetMinuteOfDay, fromMorning.targetMinuteOfDay)
        assertEquals(fromToday.placeName, fromMorning.placeName)
    }

    @Test
    fun `reports walking when the target moment is on the way`() {
        val facts = scheduleFacts(profile, placesById, TimeHint.NOW, nowMinuteOfDay = 1185)!!

        assertTrue(facts.walking)
        assertEquals("学生宿舍 3 号楼", facts.placeName)
    }

    @Test
    fun `later today wraps around midnight`() {
        val facts = scheduleFacts(profile, placesById, TimeHint.LATER_TODAY, nowMinuteOfDay = 1400)!!

        assertEquals(80, facts.targetMinuteOfDay)
    }

    @Test
    fun `no usable schedule yields nothing`() {
        val broken = NpcProfile(
            id = "npc.broken",
            name = "乙",
            oneLiner = "",
            schedule = listOf(NpcScheduleEntry(startMinute = 0, placeId = "missing")),
        )

        assertNull(scheduleFacts(broken, placesById, TimeHint.NOW, nowMinuteOfDay = 600))
    }
}
