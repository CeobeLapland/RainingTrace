package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.GridLevel
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.settings.MapFilterSettings
import com.rainingtrace.domain.settings.NpcClockOffset
import com.rainingtrace.domain.settings.NpcMessageSettings
import com.rainingtrace.domain.settings.ProactiveLevel
import com.rainingtrace.domain.settings.TrackingSettings
import com.rainingtrace.domain.world.FakeWorldStateProvider
import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.WorldCondition
import com.rainingtrace.domain.world.deriveWorldState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NpcProactiveMessageUseCaseTest {

    private class FakeMessages : NpcMessageRepository {
        val all = mutableListOf<NpcMessage>()
        override fun observeConversations(): Flow<List<NpcConversation>> = flowOf(emptyList())
        override fun observeUnreadCount(): Flow<Int> = flowOf(all.count { it.fromNpc && !it.read })
        override fun observeThread(npcId: String, limit: Int): Flow<List<NpcMessage>> =
            flowOf(all.filter { it.npcId == npcId })

        override suspend fun recent(npcId: String, limit: Int): List<NpcMessage> =
            all.filter { it.npcId == npcId }

        override suspend fun append(message: NpcMessage) {
            all += message
        }

        override suspend fun markRead(npcId: String) = Unit
    }

    private class FakeStates : NpcStateRepository {
        val states = mutableMapOf<String, NpcState>()
        override fun observeStates(): Flow<Map<String, NpcState>> = flowOf(states)
        override suspend fun stateOf(npcId: String) = states[npcId] ?: NpcState.initial(npcId)
        override suspend fun save(state: NpcState) {
            states[state.npcId] = state
        }
    }

    private class FakeFootprints : FootprintRepository {
        val events = mutableListOf<FootprintEvent>()
        override suspend fun append(event: FootprintEvent) {
            events += event
        }

        override suspend fun eventsBetween(fromEpochMs: Long, toEpochMs: Long): List<FootprintEvent> =
            events.filter { it.timestampEpochMs in fromEpochMs..toEpochMs }

        override suspend fun eventsOfType(type: FootprintEventType): List<FootprintEvent> =
            events.filter { it.eventType == type }
    }

    private class FakeNpcs(private val npcs: List<NpcProfile>) : NpcRepository {
        override suspend fun all(): List<NpcProfile> = npcs
        override suspend fun byId(id: String): NpcProfile? = npcs.firstOrNull { it.id == id }
    }

    private class FakePlaces(private val places: List<Place>) : PlaceRepository {
        override suspend fun placeById(id: String): Place? = places.firstOrNull { it.id == id }
        override suspend fun nearby(coordinate: WorldCoordinate, radiusMeters: Double): List<Place> =
            emptyList()

        override suspend fun all(): List<Place> = places
    }

    private class FakeSettings(
        var messageSettings: NpcMessageSettings = NpcMessageSettings(),
    ) : AppSettingsRepository {
        override val gridLevel: Flow<GridLevel> = flowOf(GridLevel.M)
        override suspend fun currentGridLevel() = GridLevel.M
        override suspend fun setGridLevel(level: GridLevel) = Unit
        override val locationMode: Flow<LocationMode> = flowOf(LocationMode.FAKE)
        override suspend fun currentLocationMode() = LocationMode.FAKE
        override suspend fun setLocationMode(mode: LocationMode) = Unit
        override val mapFilter: Flow<MapFilterSettings> = flowOf(MapFilterSettings())
        override suspend fun currentMapFilter() = MapFilterSettings()
        override suspend fun setMapFilter(filter: MapFilterSettings) = Unit
        override val tracking: Flow<TrackingSettings> = flowOf(TrackingSettings())
        override suspend fun currentTracking() = TrackingSettings()
        override suspend fun setTracking(settings: TrackingSettings) = Unit
        override suspend fun fogWatermarkMs(): Long? = null
        override suspend fun setFogWatermarkMs(epochMs: Long) = Unit
        override val npcClockOffset: Flow<NpcClockOffset> = flowOf(NpcClockOffset.DEFAULT)
        override suspend fun currentNpcClockOffset() = NpcClockOffset.DEFAULT
        override suspend fun setNpcClockOffset(offset: NpcClockOffset) = Unit
        override val npcMessages: Flow<NpcMessageSettings> = flowOf(messageSettings)
        override suspend fun currentNpcMessages() = messageSettings
        override suspend fun setNpcMessages(settings: NpcMessageSettings) {
            messageSettings = settings
        }
    }

    private class RuleCatalog(override val rules: List<NpcProactiveRule>) : NpcProactiveRuleCatalog

    /** 引擎 + 可推进的时钟 + 可改的天气，测试里只跟它打交道。 */
    private class Fixture(
        val useCase: NpcProactiveMessageUseCase,
        val worldState: FakeWorldStateProvider,
        val clock: FakeWorldClock,
        val messages: FakeMessages,
        val footprints: FakeFootprints,
    ) {
        suspend operator fun invoke(): NpcMessage? = useCase()

        /** 把时间推到 [instant] 并把天气设成 [kind]。 */
        fun weatherAt(kind: WeatherKind, instant: Instant) {
            clock.set(instant)
            worldState.set(deriveWorldState(instant, WeatherState(kind)))
        }
    }

    private val lake = Place(
        id = "place.bit.north_lake",
        name = "北湖",
        type = PlaceType.LAKE,
        coordinate = WorldCoordinate(39.73, 116.17),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private val lin = NpcProfile(
        id = "npc.bit.lin",
        name = "林",
        oneLiner = "",
        schedule = listOf(NpcScheduleEntry(startMinute = 0, placeId = lake.id, activity = "在看书")),
    )

    private val npcs = FakeNpcs(listOf(lin))
    private val places = FakePlaces(listOf(lake))

    private val startInstant = LocalDate.of(2026, 9, 21).atTime(12, 0).toInstant(ZoneOffset.ofHours(8))

    private fun fixture(
        rules: List<NpcProactiveRule>,
        messages: FakeMessages = FakeMessages(),
        footprints: FakeFootprints = FakeFootprints(),
        settings: FakeSettings = FakeSettings(),
        instant: Instant = startInstant,
        weather: WeatherKind = WeatherKind.CLEAR,
    ): Fixture {
        val clock = FakeWorldClock(instant)
        val worldState = FakeWorldStateProvider(deriveWorldState(instant, WeatherState(weather)))
        return Fixture(
            useCase = NpcProactiveMessageUseCase(
                clock = clock,
                npcRepository = npcs,
                placeRepository = places,
                npcPresence = NpcPresenceUseCase(npcs, places),
                rules = RuleCatalog(rules),
                messageRepository = messages,
                stateRepository = FakeStates(),
                footprintRepository = footprints,
                worldState = worldState,
                settings = settings,
            ),
            worldState = worldState,
            clock = clock,
            messages = messages,
            footprints = footprints,
        )
    }

    private fun rainRule(id: String = "rule.rain", priority: Int = 0) = NpcProactiveRule(
        id = id,
        npcId = lin.id,
        condition = NpcTriggerCondition.WeatherBecame(WeatherKind.RAINY),
        text = "下雨了，{place}这边人少。",
        priority = priority,
    )

    private fun lakeRule() = NpcProactiveRule(
        id = "rule.lake",
        npcId = lin.id,
        condition = NpcTriggerCondition.PlayerEnteredPlace(PlaceType.LAKE),
        text = "你刚去过湖边吧。",
    )

    private fun placeEvent(atEpochMs: Long) = FootprintEvent(
        id = "f$atEpochMs",
        timestampEpochMs = atEpochMs,
        coordinate = lake.coordinate,
        eventType = FootprintEventType.PLACE_OBSERVED,
        payload = mapOf("placeId" to lake.id),
    )

    @Test
    fun `startup is not treated as a weather change`() = runTest {
        val fixture = fixture(rules = listOf(rainRule()), weather = WeatherKind.LIGHT_RAIN)

        // 一启动就是雨天：不该因此触发（否则每次开 app 都收一条）
        assertNull(fixture())
        assertNull(fixture())
        assertEquals(0, fixture.messages.all.size)
    }

    @Test
    fun `a weather transition triggers the rule`() = runTest {
        val fixture = fixture(rules = listOf(rainRule()))

        fixture() // 建立基线
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, startInstant)

        val message = fixture()

        assertNotNull(message)
        assertEquals("rule.rain", message!!.ruleId)
        assertEquals(lin.id, message.npcId)
        assertEquals(false, message.read)
    }

    @Test
    fun `the slot is filled with his real place`() = runTest {
        val fixture = fixture(rules = listOf(rainRule()))

        fixture()
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, startInstant)
        fixture()

        assertEquals("下雨了，北湖这边人少。", fixture.messages.all.single().text)
    }

    @Test
    fun `the rule cooldown blocks a second send`() = runTest {
        val fixture = fixture(rules = listOf(rainRule()))

        fixture()
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, startInstant)
        assertNotNull(fixture())

        // 3 小时后：过了全局最小间隔（2h），但没过规则冷却（12h）
        val later = startInstant.plusSeconds(3 * 3600)
        fixture.weatherAt(WeatherKind.CLEAR, later)
        assertNull(fixture()) // 变晴，条件不匹配
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, later)
        assertNull(fixture()) // 又变雨，但规则还在冷却里

        assertEquals(1, fixture.messages.all.size)
    }

    @Test
    fun `the daily limit blocks a third send`() = runTest {
        val fixture = fixture(
            rules = listOf(rainRule()),
            settings = FakeSettings(NpcMessageSettings(proactiveLevel = ProactiveLevel.NORMAL)),
        )

        // NORMAL 每天 2 条：规则冷却 4h、全局最小间隔 2h，所以每轮推 5 小时。
        fixture()
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, startInstant)
        assertNotNull(fixture()) // 第 1 条

        val second = startInstant.plusSeconds(5 * 3600)
        fixture.weatherAt(WeatherKind.CLEAR, second)
        fixture()
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, second)
        assertNotNull(fixture()) // 第 2 条

        val third = second.plusSeconds(5 * 3600)
        fixture.weatherAt(WeatherKind.CLEAR, third)
        fixture()
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, third)
        assertNull(fixture()) // 今天的额度用完了

        assertEquals(2, fixture.messages.all.size)
    }

    @Test
    fun `quiet mode sends nothing`() = runTest {
        val fixture = fixture(
            rules = listOf(rainRule()),
            settings = FakeSettings(NpcMessageSettings(proactiveLevel = ProactiveLevel.QUIET)),
        )

        fixture()
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, startInstant)

        assertNull(fixture())
        assertEquals(0, fixture.messages.all.size)
    }

    @Test
    fun `nothing is sent during quiet hours`() = runTest {
        val night = LocalDate.of(2026, 9, 21).atTime(3, 0).toInstant(ZoneOffset.ofHours(8))
        val fixture = fixture(rules = listOf(rainRule()), instant = night)

        fixture()
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, night)

        assertNull(fixture())
        assertEquals(0, fixture.messages.all.size)
    }

    @Test
    fun `only footprints after the watermark count`() = runTest {
        val startMs = startInstant.toEpochMilli()
        val fixture = fixture(rules = listOf(lakeRule()))

        fixture() // 水位推到 now

        // 水位之前发生的事：不算"刚去过"
        fixture.footprints.append(placeEvent(startMs - 60_000))
        fixture.clock.set(startInstant.plusSeconds(60))
        assertNull(fixture())

        // 水位之后发生的事：算
        fixture.footprints.append(placeEvent(startMs + 120_000))
        fixture.clock.set(startInstant.plusSeconds(120))
        assertNotNull(fixture())

        assertEquals(1, fixture.messages.all.size)
    }

    @Test
    fun `only one message per check`() = runTest {
        val fixture = fixture(rules = listOf(rainRule("rule.a"), rainRule("rule.b"), lakeRule()))

        fixture()
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, startInstant)

        assertNotNull(fixture())
        assertEquals(1, fixture.messages.all.size)
    }

    @Test
    fun `the most specific rule wins`() = runTest {
        val specific = NpcProactiveRule(
            id = "rule.specific",
            npcId = lin.id,
            condition = NpcTriggerCondition.All(
                listOf(
                    NpcTriggerCondition.WeatherBecame(WeatherKind.RAINY),
                    NpcTriggerCondition.World(WorldCondition.TimeOfDayIn(setOf(TimeOfDay.DAY))),
                ),
            ),
            text = "白天这场雨下得挺久。",
        )
        val fixture = fixture(rules = listOf(rainRule("rule.plain"), specific))

        fixture()
        fixture.weatherAt(WeatherKind.LIGHT_RAIN, startInstant)

        assertEquals("rule.specific", fixture()!!.ruleId)
    }
}
