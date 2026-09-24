package com.rainingtrace.domain.inventory

import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.WorldCoordinate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadResourceDiscoveriesUseCaseTest {

    private val somewhere = WorldCoordinate(39.7326, 116.1712)

    private class FakeFootprintRepository(
        private val events: List<FootprintEvent>,
    ) : FootprintRepository {
        override suspend fun append(event: FootprintEvent) = Unit
        override suspend fun eventsBetween(fromEpochMs: Long, toEpochMs: Long): List<FootprintEvent> =
            events

        override suspend fun eventsOfType(type: FootprintEventType): List<FootprintEvent> =
            events.filter { it.eventType == type }
    }

    private class FakeInventoryRepository(
        private val state: InventoryState,
    ) : InventoryRepository {
        override fun observeState(): Flow<InventoryState> = flowOf(state)
        override suspend fun loadState(): InventoryState = state
        override suspend fun saveState(state: InventoryState) = Unit
    }

    private fun acquired(resourceId: String, atEpochMs: Long) = FootprintEvent(
        id = "e-$atEpochMs-$resourceId",
        timestampEpochMs = atEpochMs,
        coordinate = somewhere,
        eventType = FootprintEventType.PLACE_OBSERVED,
        payload = mapOf("resourceId" to resourceId),
    )

    private fun holdings(vararg items: Pair<String, Long>) = InventoryState(
        items = items.associate { (id, firstAt) -> id to InventoryItem(id, 1, firstAt, firstAt) },
    )

    @Test
    fun `采过的东西都算发现`() = runTest {
        val useCase = LoadResourceDiscoveriesUseCase(
            footprintRepository = FakeFootprintRepository(listOf(acquired("res.moss", 1_000))),
            inventoryRepository = FakeInventoryRepository(InventoryState()),
        )

        val discoveries = useCase()

        assertEquals(1_000, discoveries.getValue("res.moss").firstAtEpochMs)
    }

    @Test
    fun `背包里做出来的东西也算发现`() = runTest {
        val useCase = LoadResourceDiscoveriesUseCase(
            footprintRepository = FakeFootprintRepository(emptyList()),
            inventoryRepository = FakeInventoryRepository(holdings("res.rope" to 2_000)),
        )

        assertTrue("res.rope" in useCase())
    }

    @Test
    fun `仓库里的东西也算发现`() = runTest {
        val useCase = LoadResourceDiscoveriesUseCase(
            footprintRepository = FakeFootprintRepository(emptyList()),
            inventoryRepository = FakeInventoryRepository(InventoryState()),
            warehouseRepository = FakeInventoryRepository(holdings("res.wood_tag" to 3_000)),
        )

        assertEquals(3_000, useCase().getValue("res.wood_tag").firstAtEpochMs)
    }
}