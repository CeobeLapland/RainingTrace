package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcCommitmentTest {

    private fun place(id: String, name: String, lng: Double) = Place(
        id = id,
        name = name,
        type = PlaceType.OTHER,
        coordinate = WorldCoordinate(39.0, lng),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    /** a 与 b 相距约 86m（0.001 度经度），步行约 2 分钟。 */
    private val places = mapOf(
        "a" to place("a", "图书馆", 116.0),
        "b" to place("b", "食堂", 116.001),
        "c" to place("c", "湖心花园", 116.02),
    )

    /** 10:00 到图书馆，之后一整天都在那儿（下一条是次日 10:00）。 */
    private val settlesAtLibrary = NpcProfile(
        id = "npc.lin",
        name = "林",
        oneLiner = "",
        schedule = listOf(NpcScheduleEntry(startMinute = 600, placeId = "a", activity = "在看书")),
    )

    /** 10:00 在图书馆，10:05 立刻去花园（只有 5 分钟，走不到）。 */
    private val leavingSoon = NpcProfile(
        id = "npc.busy",
        name = "忙",
        oneLiner = "",
        schedule = listOf(
            NpcScheduleEntry(startMinute = 600, placeId = "a", activity = "在看书"),
            NpcScheduleEntry(startMinute = 605, placeId = "c"),
        ),
    )

    @Test
    fun `he agrees when he is already there`() {
        // 10:30 问"在图书馆见"：他本来就在图书馆
        assertEquals(
            CommitmentAnswer.AlreadyThere,
            answerCommitment(settlesAtLibrary, places, 630, "a"),
        )
    }

    @Test
    fun `he agrees with a travel estimate when he can make it`() {
        val answer = answerCommitment(settlesAtLibrary, places, 630, "b")

        assertTrue(answer is CommitmentAnswer.Agree)
        // 86m / 80m每分钟 → 2 分钟（向上取整）
        assertEquals(2, (answer as CommitmentAnswer.Agree).travelMinutes)
    }

    @Test
    fun `he refuses when another appointment is too close`() {
        // 10:02 要去花园（18 分钟后到不了），所以走不开
        assertEquals(CommitmentAnswer.Busy, answerCommitment(leavingSoon, places, 602, "c"))
    }

    @Test
    fun `a broken schedule gives no answer at all`() {
        val broken = NpcProfile(
            id = "npc.x",
            name = "乙",
            oneLiner = "",
            schedule = listOf(NpcScheduleEntry(startMinute = 0, placeId = "missing")),
        )

        assertEquals(CommitmentAnswer.Unknown, answerCommitment(broken, places, 600, "a"))
        assertEquals(CommitmentAnswer.Unknown, answerCommitment(settlesAtLibrary, places, 600, "nope"))
    }

    @Test
    fun `a commitment override moves him to the agreed place`() {
        val override = NpcScheduleOverride(
            placeId = "b",
            startMinute = 840,
            endMinute = 900,
            activity = "在这儿等你",
        )

        val schedule = resolveSchedule(settlesAtLibrary, places, listOf(override))
        val atAgreedTime = schedule.presenceAt(860)!!

        assertEquals("b", atAgreedTime.placeId)
        assertEquals("在这儿等你", atAgreedTime.activity)
        // 覆盖窗口之外仍是原作息
        assertEquals("a", schedule.presenceAt(700)!!.placeId)
    }

    @Test
    fun `the override only replaces entries inside its window`() {
        val profile = NpcProfile(
            id = "npc.zhou",
            name = "周",
            oneLiner = "",
            schedule = listOf(
                NpcScheduleEntry(startMinute = 600, placeId = "a", activity = "在食堂帮忙"),
                NpcScheduleEntry(startMinute = 1200, placeId = "b", activity = "回宿舍"),
            ),
        )
        val override = NpcScheduleOverride(
            placeId = "b",
            startMinute = 600,
            endMinute = 660,
            activity = "在这儿等你",
        )

        val schedule = resolveSchedule(profile, places, listOf(override))

        assertEquals("b", schedule.presenceAt(620)!!.placeId) // 窗口内被覆盖
        assertNotNull(schedule.presenceAt(1200)) // 窗口外的条目还在
        assertEquals("b", schedule.presenceAt(1200)!!.placeId)
    }

    @Test
    fun `the override window must not wrap around midnight`() {
        val error = runCatching {
            NpcScheduleOverride(
                placeId = "a",
                startMinute = 1380,
                endMinute = 60,
                activity = "在这儿等你",
            )
        }.exceptionOrNull()

        assertNotNull(error)
    }

    @Test
    fun `a commitment can be closed and stops being open`() {
        val commitment = NpcCommitment(
            id = "c1",
            npcId = "npc.lin",
            placeId = "a",
            dateKey = "2026-09-21",
            startMinute = 840,
            endMinute = 900,
            createdAtEpochMs = 0,
        )

        assertTrue(commitment.isOpen)
        assertFalse(commitment.copy(status = NpcCommitmentStatus.KEPT).isOpen)
        assertFalse(commitment.copy(status = NpcCommitmentStatus.MISSED).isOpen)
    }

    @Test
    fun `the time hint knows which day it points at`() {
        val today = java.time.LocalDate.of(2026, 9, 21)

        assertEquals("2026-09-21", TimeHint.TONIGHT.resolveDateKey(today))
        assertEquals("2026-09-22", TimeHint.TOMORROW_AFTERNOON.resolveDateKey(today))
        // 跨月也要对
        assertEquals("2026-10-01", TimeHint.TOMORROW_MORNING.resolveDateKey(java.time.LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun `promises are only allowed when a commitment was saved`() {
        assertFalse(DialogueValidator.validate("说好了，明天下午我在图书馆。"))
        assertTrue(DialogueValidator.validate("说好了，明天下午我在图书馆。", allowPromises = true))
        // 放行承诺词，但长度与占位符仍然要守
        assertFalse(DialogueValidator.validate("", allowPromises = true))
        assertFalse(DialogueValidator.validate("{place}", allowPromises = true))
    }
}