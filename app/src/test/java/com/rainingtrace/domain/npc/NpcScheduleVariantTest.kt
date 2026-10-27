package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.WorldCondition
import com.rainingtrace.domain.world.WorldState
import com.rainingtrace.domain.world.timeOfDayOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * N3：作息变体（雨天/周末/外出/生病）+ 停留期确定性溜达。
 *
 * 两个口径必须钉死：
 * 1. **同一天同一时刻同结果**（否则地图每 10 秒刷新，NPC 会横跳）；
 * 2. **世界状态只做门控**，不参与随机挑选。
 */
class NpcScheduleVariantTest {

    private val dorm = place("place.bit.dorm_3", PlaceType.DORM)
    private val library = place("place.bit.library", PlaceType.LIBRARY)
    private val canteen = place("place.bit.canteen_one", PlaceType.CANTEEN)
    private val places = listOf(dorm, library, canteen).associateBy { it.id }

    private val baseSchedule = listOf(
        NpcScheduleEntry(420, dorm.id, 0, "起床"),
        NpcScheduleEntry(540, library.id, 20, "自习"),
        NpcScheduleEntry(1200, dorm.id, 20, "回去"),
    )

    private val rainVariant = NpcScheduleVariant(
        id = "zhou.rain",
        label = "雨天",
        schedule = listOf(
            NpcScheduleEntry(420, canteen.id, 0, "屋檐下看雨"),
            NpcScheduleEntry(1200, dorm.id, 10, "回宿舍"),
        ),
        conditions = listOf(WorldCondition.WeatherIn(setOf(WeatherKind.LIGHT_RAIN, WeatherKind.HEAVY_RAIN))),
        priority = 10,
    )

    private fun state(
        date: LocalDate = LocalDate.of(2026, 10, 27),
        weather: WeatherKind = WeatherKind.CLEAR,
        minute: Int = 600,
    ) = WorldState(
        instant = ZonedDateTime.of(date.atTime(minute / 60, minute % 60), WORLD_ZONE).toInstant(),
        localDate = date,
        minuteOfDay = minute,
        timeOfDay = timeOfDayOf(minute),
        weather = WeatherState(weather),
        season = Season.AUTUMN,
    )

    private fun place(id: String, type: PlaceType) = Place(
        id = id,
        name = id.substringAfterLast('.'),
        type = type,
        coordinate = WorldCoordinate(39.7326, 116.1712),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private fun profile(vararg variants: NpcScheduleVariant) = NpcProfile(
        id = "npc.bit.zhou",
        name = "周野",
        oneLiner = "",
        schedule = baseSchedule,
        scheduleVariants = variants.toList(),
    )

    // ---- 变体选择 ----

    @Test
    fun `晴天用基础作息 雨天切换成雨天变体`() {
        val p = profile(rainVariant)

        assertNull("晴天不该命中雨天变体", selectScheduleVariant(p, state(weather = WeatherKind.CLEAR)))
        assertEquals("zhou.rain", selectScheduleVariant(p, state(weather = WeatherKind.HEAVY_RAIN))?.id)
    }

    @Test
    fun `变体真的改变了此刻位置`() {
        val p = profile(rainVariant)

        val sunny = resolveSchedule(p, places, variant = selectScheduleVariant(p, state())).presenceAt(600)!!
        val rainy = resolveSchedule(p, places, variant = selectScheduleVariant(p, state(weather = WeatherKind.LIGHT_RAIN)))
            .presenceAt(600)!!

        assertEquals("晴天在图书馆自习", library.id, sunny.placeId)
        assertEquals("雨天改在食堂屋檐下", canteen.id, rainy.placeId)
    }

    @Test
    fun `日期窗口到期后自动回到基础作息`() {
        val trip = NpcScheduleVariant(
            id = "liao.trip",
            label = "外出考试",
            schedule = emptyList(), // 空 = 不在校
            fromDateKey = "2026-12-20",
            toDateKey = "2026-12-22",
            priority = 100,
        )
        val p = profile(trip)

        assertEquals("窗口内生效", "liao.trip", selectScheduleVariant(p, state(LocalDate.of(2026, 12, 21)))?.id)
        assertNull("窗口前一天不该生效", selectScheduleVariant(p, state(LocalDate.of(2026, 12, 19))))
        assertNull("窗口后一天不该生效", selectScheduleVariant(p, state(LocalDate.of(2026, 12, 23))))
        assertNull(
            "空作息变体 = 今天不出现",
            resolveSchedule(p, places, variant = selectScheduleVariant(p, state(LocalDate.of(2026, 12, 21))))
                .presenceAt(600),
        )
    }

    @Test
    fun `周末变体只在指定的星期生效`() {
        val weekend = NpcScheduleVariant(
            id = "x.weekend",
            label = "周末",
            schedule = listOf(NpcScheduleEntry(600, canteen.id, 0, "周末去食堂慢慢吃")),
            weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        )
        val p = profile(weekend)

        // 2026-10-24 是周六，2026-10-27 是周二
        assertEquals("x.weekend", selectScheduleVariant(p, state(LocalDate.of(2026, 10, 24)))?.id)
        assertNull(selectScheduleVariant(p, state(LocalDate.of(2026, 10, 27))))
    }

    @Test
    fun `多个变体同时命中时按优先级再按 id 确定性取一`() {
        val low = rainVariant.copy(id = "aaa", priority = 1)
        val high = rainVariant.copy(id = "zzz", priority = 5)
        val tieA = rainVariant.copy(id = "aaa", priority = 5)
        val tieZ = rainVariant.copy(id = "zzz", priority = 5)

        val rainy = state(weather = WeatherKind.HEAVY_RAIN)
        assertEquals("优先级高的胜出", "zzz", selectScheduleVariant(profile(low, high), rainy)?.id)
        assertEquals("并列时 id 字典序取大者（确定性）", "zzz", selectScheduleVariant(profile(tieA, tieZ), rainy)?.id)
    }

    @Test
    fun `承诺覆盖仍高于一切变体`() {
        val p = profile(rainVariant)
        val rainy = state(weather = WeatherKind.HEAVY_RAIN)
        val override = NpcScheduleOverride(
            placeId = library.id,
            startMinute = 600,
            endMinute = 660,
            activity = "在这儿等你",
            travelMinutes = 0,
        )

        val presence = resolveSchedule(p, places, listOf(override), selectScheduleVariant(p, rainy))
            .presenceAt(620)!!

        assertEquals("答应了就一定在约定地点", library.id, presence.placeId)
        assertEquals("在这儿等你", presence.activity)
    }

    // ---- 停留期溜达 ----

    @Test
    fun `同一天同一时刻位置完全一致 不同天会变`() {
        val base = dorm.coordinate
        val a = NpcWander.coordinateAt(base, "npc.bit.zhou", "2026-10-27", 420, 600)
        val b = NpcWander.coordinateAt(base, "npc.bit.zhou", "2026-10-27", 420, 600)
        val otherDay = NpcWander.coordinateAt(base, "npc.bit.zhou", "2026-10-28", 420, 600)

        assertEquals(a, b)
        assertTrue("换一天该换站位", a != otherDay)
    }

    @Test
    fun `溜达不会离开地点太远`() {
        val base = dorm.coordinate
        (0 until 24 * 60 step 7).forEach { minute ->
            val moved = NpcWander.coordinateAt(
                base, "npc.bit.zhou", "2026-10-27", 420, minute,
            )
            assertTrue(
                "偏移必须 ≤ ${NpcWander.RADIUS_METERS}m，实际 ${base.distanceMetersTo(moved)}",
                base.distanceMetersTo(moved) <= NpcWander.RADIUS_METERS + 0.5,
            )
        }
    }

    @Test
    fun `档内前几分钟是平滑过渡 不会整点瞬移`() {
        val base = dorm.coordinate
        val at40 = NpcWander.coordinateAt(base, "npc.bit.zhou", "2026-10-27", 420, 460)
        val at41 = NpcWander.coordinateAt(base, "npc.bit.zhou", "2026-10-27", 420, 461)

        // 20 分钟一档：00 分换档，前 3 分钟过渡。逐分钟位移应该是小步，不是跳变。
        assertTrue("换档第一分钟位移应很小", at40.distanceMetersTo(at41) < NpcWander.RADIUS_METERS)
    }

    @Test
    fun `presenceAt 传日期才会溜达 不传则站定`() {
        val p = profile()
        val schedule = resolveSchedule(p, places)
        val still = schedule.presenceAt(600)!!
        val wandering = schedule.presenceAt(600, "2026-10-27")!!

        assertEquals(dorm.coordinate, still.coordinate)
        assertTrue("传日期后应偏离地点坐标", wandering.coordinate != still.coordinate)
        assertEquals("地点语义不变", still.placeName, wandering.placeName)
    }
}
