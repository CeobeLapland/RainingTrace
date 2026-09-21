package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.FakeWorldStateProvider
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.deriveWorldState
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordNpcEncounterUseCaseTest {

    private class FakeFootprintRepository : FootprintRepository {
        val events = mutableListOf<FootprintEvent>()

        override suspend fun append(event: FootprintEvent) {
            events += event
        }

        override suspend fun eventsBetween(fromEpochMs: Long, toEpochMs: Long): List<FootprintEvent> =
            events.filter { it.timestampEpochMs in fromEpochMs..toEpochMs }

        override suspend fun eventsOfType(type: FootprintEventType): List<FootprintEvent> =
            events.filter { it.eventType == type }
    }

    private val playerCoordinate = WorldCoordinate(39.0, 116.0)

    private val presence = NpcPresence(
        npcId = "npc.bit.lin",
        npcName = "林",
        coordinate = WorldCoordinate(39.0, 116.0),
        placeId = "place.bit.library",
        placeName = "图书馆",
        activity = "在靠窗的位置自习",
        walking = false,
        progress = 0.0,
    )

    private fun useCase(footprints: FakeFootprintRepository) = RecordNpcEncounterUseCase(
        clock = FakeWorldClock(
            LocalDate.of(2026, 9, 21).atTime(14, 0).toInstant(ZoneOffset.ofHours(8)),
        ),
        footprintRepository = footprints,
        worldState = FakeWorldStateProvider(
            deriveWorldState(
                instant = LocalDate.of(2026, 9, 21).atTime(14, 0).toInstant(ZoneOffset.ofHours(8)),
                weather = WeatherState(WeatherKind.CLEAR),
            ),
        ),
    )

    @Test
    fun `first meeting within range is recorded`() = runTest {
        val footprints = FakeFootprintRepository()

        val result = useCase(footprints)(playerCoordinate, presence)

        assertEquals(NpcEncounterResult.Met("npc.bit.lin", "林"), result)
        val event = footprints.events.single()
        assertEquals(FootprintEventType.NPC_MET, event.eventType)
        assertEquals("npc.bit.lin", event.payload["npcId"])
        assertEquals(playerCoordinate, event.coordinate)
    }

    @Test
    fun `the event carries the world state at the time of the meeting`() = runTest {
        val footprints = FakeFootprintRepository()

        useCase(footprints)(playerCoordinate, presence)

        val payload = footprints.events.single().payload
        assertEquals("place.bit.library", payload["placeId"])
        assertEquals("在靠窗的位置自习", payload["activity"])
        assertEquals("CLEAR", payload["weather"])
        assertEquals("DAY", payload["timeOfDay"])
    }

    @Test
    fun `a second meeting is not recorded again`() = runTest {
        val footprints = FakeFootprintRepository()
        val record = useCase(footprints)

        record(playerCoordinate, presence)
        val second = record(playerCoordinate, presence)

        assertEquals(NpcEncounterResult.AlreadyMet("npc.bit.lin", "林"), second)
        assertEquals(1, footprints.events.size)
    }

    @Test
    fun `an npc out of range is not recorded`() = runTest {
        val footprints = FakeFootprintRepository()
        // 0.001 度经度 ≈ 86m（北纬 39 度），远超 30m
        val far = WorldCoordinate(39.0, 116.001)

        val result = useCase(footprints)(far, presence)

        assertEquals(NpcEncounterResult.TooFar, result)
        assertTrue(footprints.events.isEmpty())
    }

    @Test
    fun `other footprint types do not count as already met`() = runTest {
        val footprints = FakeFootprintRepository()
        footprints.append(
            FootprintEvent(
                id = "seed",
                timestampEpochMs = 0L,
                coordinate = playerCoordinate,
                eventType = FootprintEventType.PLACE_OBSERVED,
                payload = mapOf("npcId" to presence.npcId),
            ),
        )

        val result = useCase(footprints)(playerCoordinate, presence)

        assertEquals(NpcEncounterResult.Met("npc.bit.lin", "林"), result)
    }
}
