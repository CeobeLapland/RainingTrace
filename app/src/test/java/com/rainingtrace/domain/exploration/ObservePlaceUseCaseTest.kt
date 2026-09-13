package com.rainingtrace.domain.exploration

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.InventoryState
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ObservePlaceUseCaseTest {

    private val lake = Place(
        id = "place.lake",
        name = "北湖",
        type = PlaceType.LAKE,
        coordinate = WorldCoordinate(39.7326, 116.1712),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private val grid = HexGrid(WorldCoordinate(39.7326, 116.1712), cellSizeMeters = 80.0)
    private val clock = FakeWorldClock(Instant.parse("2026-09-13T08:00:00Z"))

    private class FakeInventoryRepository : InventoryRepository {
        var state = InventoryState()
        override fun observeState(): Flow<InventoryState> = flowOf(state)
        override suspend fun loadState(): InventoryState = state
        override suspend fun saveState(state: InventoryState) {
            this.state = state
        }
    }

    private class FakeFootprintRepository : FootprintRepository {
        val events = mutableListOf<FootprintEvent>()
        override suspend fun append(event: FootprintEvent) {
            events.add(event)
        }
        override suspend fun eventsBetween(fromEpochMs: Long, toEpochMs: Long): List<FootprintEvent> =
            events.filter { it.timestampEpochMs in fromEpochMs..toEpochMs }
    }

    private fun useCase(
        inventory: FakeInventoryRepository = FakeInventoryRepository(),
        footprint: FakeFootprintRepository = FakeFootprintRepository(),
    ) = Triple(
        ObservePlaceUseCase(
            grid = grid,
            clock = clock,
            inventoryRepository = inventory,
            addItem = AddItemToInventoryUseCase(clock),
            footprintRepository = footprint,
        ),
        inventory,
        footprint,
    )

    @Test
    fun `observe in range grants resource and logs footprint`() = runTest {
        val (observe, inventory, footprint) = useCase()
        val result = observe(lake.coordinate, lake)

        assertTrue(result is ObserveResult.Success)
        result as ObserveResult.Success
        assertEquals(1, result.newQuantity)
        assertEquals(1, inventory.state.quantityOf("res.observation_record"))
        assertEquals(1, footprint.events.size)
        assertEquals(FootprintEventType.PLACE_OBSERVED, footprint.events.first().eventType)
        assertEquals("place.lake", footprint.events.first().payload["placeId"])
    }

    @Test
    fun `observe too far rejected`() = runTest {
        val (observe, inventory, footprint) = useCase()
        val far = WorldCoordinate(39.7426, 116.1712) // 约 1.1km 北
        val result = observe(far, lake)

        assertEquals(ObserveRejectReason.TOO_FAR, (result as ObserveResult.Rejected).reason)
        assertEquals(0, inventory.state.totalKinds())
        assertTrue(footprint.events.isEmpty())
    }

    @Test
    fun `second observe within cooldown rejected`() = runTest {
        val (observe, _, footprint) = useCase()
        assertTrue(observe(lake.coordinate, lake) is ObserveResult.Success)

        clock.advanceSeconds(60)
        val second = observe(lake.coordinate, lake)
        assertEquals(ObserveRejectReason.ON_COOLDOWN, (second as ObserveResult.Rejected).reason)
        assertEquals(1, footprint.events.size)
    }

    @Test
    fun `observe again after cooldown succeeds`() = runTest {
        val (observe, inventory, _) = useCase()
        assertTrue(observe(lake.coordinate, lake) is ObserveResult.Success)

        clock.advanceSeconds(11 * 60)
        val second = observe(lake.coordinate, lake)
        assertTrue(second is ObserveResult.Success)
        assertEquals(2, (second as ObserveResult.Success).newQuantity)
        assertEquals(2, inventory.state.quantityOf("res.observation_record"))
    }

    @Test
    fun `observe at edge of range succeeds`() = runTest {
        val (observe, _, _) = useCase()
        // 约 110m 北，在 120m 范围内
        val edge = WorldCoordinate(39.73359, 116.1712)
        assertTrue(observe(edge, lake) is ObserveResult.Success)
    }
}
