package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.settings.NpcClockOffset
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.deriveWorldState
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NpcPresenceUseCaseTest {

    private class FakeNpcRepository(private val npcs: List<NpcProfile>) : NpcRepository {
        override suspend fun all(): List<NpcProfile> = npcs
        override suspend fun byId(id: String): NpcProfile? = npcs.firstOrNull { it.id == id }
    }

    private class FakePlaceRepository(private val places: List<Place>) : PlaceRepository {
        override suspend fun placeById(id: String): Place? = places.firstOrNull { it.id == id }
        override suspend fun nearby(coordinate: WorldCoordinate, radiusMeters: Double): List<Place> =
            emptyList()

        override suspend fun all(): List<Place> = places
    }

    private fun place(id: String, lng: Double) = Place(
        id = id,
        name = "地点$id",
        type = PlaceType.OTHER,
        coordinate = WorldCoordinate(39.0, lng),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private val places = listOf(place("a", 116.0), place("b", 117.0))

    private val npcs = listOf(
        NpcProfile(
            id = "npc.a",
            name = "甲",
            oneLiner = "",
            schedule = listOf(
                NpcScheduleEntry(startMinute = 600, placeId = "a"),
                NpcScheduleEntry(startMinute = 1200, placeId = "b", travelMinutes = 30),
            ),
        ),
        NpcProfile(
            id = "npc.broken",
            name = "乙",
            oneLiner = "",
            schedule = listOf(NpcScheduleEntry(startMinute = 0, placeId = "missing")),
        ),
    )

    private val useCase = NpcPresenceUseCase(FakeNpcRepository(npcs), FakePlaceRepository(places))

    /** 用本地时间构造世界状态，让 minuteOfDay 落在期望的时刻。 */
    private fun stateAt(hour: Int, minute: Int = 0) = deriveWorldState(
        instant = LocalDate.of(2026, 9, 21).atTime(hour, minute).toInstant(ZoneOffset.ofHours(8)),
        weather = WeatherState(WeatherKind.CLEAR),
    )

    @Test
    fun `presence is computed from the state minute of day`() = runTest {
        // 20:00 已在 b（20:00 到达，行走窗口是 19:30-20:00 之前）
        val atEight = useCase.presenceOf("npc.a", stateAt(20, 15))!!
        assertEquals("b", atEight.placeId)
        assertFalse(atEight.walking)

        // 10:30 还在 a（下一段行程 19:30 才开始）
        val atMorning = useCase.presenceOf("npc.a", stateAt(10, 30))!!
        assertEquals("a", atMorning.placeId)
    }

    @Test
    fun `presences at returns every npc with a usable schedule`() = runTest {
        val presences = useCase.presencesAt(stateAt(10, 30))

        // "乙" 的作息指向不存在的地点 → 被跳过
        assertEquals(1, presences.size)
        assertEquals("npc.a", presences.single().npcId)
    }

    @Test
    fun `unknown npc id returns null`() = runTest {
        assertNull(useCase.presenceOf("npc.nobody", stateAt(10, 30)))
    }

    @Test
    fun `debug offset shifts which place the npc is at`() = runTest {
        val offset = MutableStateFlow(NpcClockOffset.NONE)
        val shifted = NpcPresenceUseCase(
            FakeNpcRepository(npcs),
            FakePlaceRepository(places),
            offset,
        )

        // 真实 20:15：他已经在 b 了
        assertEquals("b", shifted.presenceOf("npc.a", stateAt(20, 15))!!.placeId)

        // 往回挪 3 小时 → 17:15，那时他还在 a
        offset.value = NpcClockOffset.MINUS_3H
        assertEquals("a", shifted.presenceOf("npc.a", stateAt(20, 15))!!.placeId)
    }

    @Test
    fun `debug offset wraps around midnight`() = runTest {
        val shifted = NpcPresenceUseCase(
            FakeNpcRepository(npcs),
            FakePlaceRepository(places),
            MutableStateFlow(NpcClockOffset.PLUS_6H),
        )

        // 真实 20:15 + 6h = 次日 02:15；作息按天环，所以结果应等于"02:15"那次的求值
        assertEquals(
            useCase.presenceOf("npc.a", stateAt(2, 15))!!.placeId,
            shifted.presenceOf("npc.a", stateAt(20, 15))!!.placeId,
        )
    }
}
