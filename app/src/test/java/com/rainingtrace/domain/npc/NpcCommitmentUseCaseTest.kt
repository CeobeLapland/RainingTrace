package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.FakeWorldStateProvider
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.deriveWorldState
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcCommitmentUseCaseTest {

    private class FakeCommitments : NpcCommitmentRepository {
        val all = mutableListOf<NpcCommitment>()
        override suspend fun all(): List<NpcCommitment> = all
        override suspend fun save(commitment: NpcCommitment) {
            val index = all.indexOfFirst { it.id == commitment.id }
            if (index >= 0) all[index] = commitment else all += commitment
        }
    }

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

    private class FakePlaces(private val places: List<Place>) : PlaceRepository {
        override suspend fun placeById(id: String): Place? = places.firstOrNull { it.id == id }
        override suspend fun nearby(coordinate: WorldCoordinate, radiusMeters: Double): List<Place> =
            emptyList()

        override suspend fun all(): List<Place> = places
    }

    private val library = Place(
        id = "place.bit.library",
        name = "图书馆",
        type = PlaceType.LIBRARY,
        coordinate = WorldCoordinate(39.73, 116.17),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private val near = WorldCoordinate(39.7301, 116.17) // 约 11m
    private val far = WorldCoordinate(39.75, 116.17) // 约 2.2km

    private val startInstant =
        LocalDate.of(2026, 9, 21).atTime(14, 30).toInstant(ZoneOffset.ofHours(8))

    private class Fixture(
        val useCase: NpcCommitmentUseCase,
        val commitments: FakeCommitments,
        val messages: FakeMessages,
        val footprints: FakeFootprints,
        val states: FakeStates,
        val clock: FakeWorldClock,
        val worldState: FakeWorldStateProvider,
    ) {
        suspend fun tick(playerCoordinate: WorldCoordinate?) = useCase.tick(playerCoordinate)

        fun advanceTo(minuteOfDay: Int, day: Int = 21) {
            val instant = LocalDate.of(2026, 9, day)
                .atTime(minuteOfDay / 60, minuteOfDay % 60)
                .toInstant(ZoneOffset.ofHours(8))
            clock.set(instant)
            worldState.set(deriveWorldState(instant, WeatherState(WeatherKind.CLEAR)))
        }
    }

    private fun fixture(instant: java.time.Instant = startInstant): Fixture {
        val commitments = FakeCommitments()
        val messages = FakeMessages()
        val footprints = FakeFootprints()
        val states = FakeStates()
        val clock = FakeWorldClock(instant)
        val worldState = FakeWorldStateProvider(
            deriveWorldState(instant, WeatherState(WeatherKind.CLEAR)),
        )
        return Fixture(
            useCase = NpcCommitmentUseCase(
                clock = clock,
                commitments = commitments,
                placeRepository = FakePlaces(listOf(library)),
                messageWriter = NpcMessageWriter(messages, footprints),
                stateRepository = states,
                footprintRepository = footprints,
                worldState = worldState,
            ),
            commitments = commitments,
            messages = messages,
            footprints = footprints,
            states = states,
            clock = clock,
            worldState = worldState,
        )
    }

    /** 14:30 的约定，窗口到 15:30。 */
    private fun agreed(
        dateKey: String = "2026-09-21",
        startMinute: Int = 14 * 60 + 30,
        endMinute: Int = 15 * 60 + 30,
    ) = NpcCommitment(
        id = "c1",
        npcId = "npc.bit.lin",
        placeId = library.id,
        dateKey = dateKey,
        startMinute = startMinute,
        endMinute = endMinute,
        createdAtEpochMs = 0,
    )

    @Test
    fun `nothing happens before the agreed time`() = runTest {
        val fixture = fixture()
        fixture.commitments.save(agreed(startMinute = 16 * 60))

        assertNull(fixture.tick(near))
        assertTrue(fixture.commitments.all.single().isOpen)
    }

    @Test
    fun `he checks in as soon as the player arrives`() = runTest {
        val fixture = fixture()
        fixture.commitments.save(agreed())

        val message = fixture.tick(near)

        assertNotNull(message)
        assertEquals("我到了，在「图书馆」。", message!!.text)
        assertEquals(NpcCommitmentStatus.KEPT, fixture.commitments.all.single().status)
        assertTrue(fixture.messages.all.single().let { it.fromNpc && !it.read })
    }

    @Test
    fun `keeping the appointment raises affection once`() = runTest {
        val fixture = fixture()
        fixture.commitments.save(agreed())

        fixture.tick(near)
        val afterKeep = fixture.states.states.getValue("npc.bit.lin").affection
        assertEquals(2, afterKeep)

        // 已兑现，再 tick 不会重复发消息或加好感
        assertNull(fixture.tick(near))
        assertEquals(afterKeep, fixture.states.states.getValue("npc.bit.lin").affection)
        assertEquals(1, fixture.messages.all.size)
    }

    @Test
    fun `the kept footprint is recorded for the archive`() = runTest {
        val fixture = fixture()
        fixture.commitments.save(agreed())

        fixture.tick(near)

        val event = fixture.footprints.events.single {
            it.eventType == FootprintEventType.NPC_COMMITMENT_KEPT
        }
        assertEquals("c1", event.payload["commitmentId"])
        assertEquals("npc.bit.lin", event.payload["npcId"])
    }

    @Test
    fun `a player who never comes is marked as missed`() = runTest {
        val fixture = fixture()
        fixture.commitments.save(agreed())

        // 还在窗口内、人也没到：什么都不做（他继续等）
        assertNull(fixture.tick(far))
        assertTrue(fixture.commitments.all.single().isOpen)

        // 窗口过了：记一次"没等到"，不立刻发消息（他下次聊天会提）
        fixture.advanceTo(15 * 60 + 31)
        assertNull(fixture.tick(far))

        assertEquals(NpcCommitmentStatus.MISSED, fixture.commitments.all.single().status)
        assertTrue(
            fixture.footprints.events.any {
                it.eventType == FootprintEventType.NPC_COMMITMENT_MISSED
            },
        )
        assertTrue(fixture.messages.all.isEmpty())
    }

    @Test
    fun `yesterday's appointment is resolved even if the app was closed`() = runTest {
        // 引擎只在应用可见时跑，所以"昨天"的约定要能在今天补算出结果。
        val fixture = fixture()
        fixture.commitments.save(agreed(dateKey = "2026-09-20"))
        fixture.advanceTo(9 * 60, day = 21)

        fixture.tick(near)

        assertEquals(NpcCommitmentStatus.KEPT, fixture.commitments.all.single().status)
    }

    @Test
    fun `a missing place is resolved instead of hanging forever`() = runTest {
        val fixture = fixture()
        fixture.commitments.save(agreed().copy(placeId = "place.gone"))

        fixture.tick(near)

        assertEquals(NpcCommitmentStatus.MISSED, fixture.commitments.all.single().status)
        assertTrue(fixture.messages.all.isEmpty())
    }
}