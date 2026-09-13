package com.rainingtrace.domain.memory

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.WorldCoordinate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CreateMemoryUseCaseTest {

    private val grid = HexGrid(WorldCoordinate(39.7326, 116.1712), cellSizeMeters = 80.0)
    private val clock = FakeWorldClock(Instant.parse("2026-09-13T08:00:00Z"))

    private class FakeMemoryRepository : MemoryRepository {
        val saved = mutableListOf<MemoryNode>()
        override suspend fun save(memory: MemoryNode) { saved.add(memory) }
        override suspend fun byCell(cellId: HexCellId): List<MemoryNode> =
            saved.filter { it.cellId == cellId }
        override suspend fun latest(limit: Int): List<MemoryNode> = saved.takeLast(limit).reversed()
    }

    private class FakeExplorationRepository : ExplorationRepository {
        var state = ExplorationState()
        override fun observeState(): Flow<ExplorationState> = flowOf(state)
        override suspend fun loadState(): ExplorationState = state
        override suspend fun saveStates(states: Map<HexCellId, CellFogState>) {
            var s = state
            states.forEach { (c, st) -> s = s.withState(c, st) }
            state = s
        }
    }

    private class FakeFootprintRepository : FootprintRepository {
        val events = mutableListOf<FootprintEvent>()
        override suspend fun append(event: FootprintEvent) { events.add(event) }
        override suspend fun eventsBetween(fromEpochMs: Long, toEpochMs: Long): List<FootprintEvent> =
            events.filter { it.timestampEpochMs in fromEpochMs..toEpochMs }
    }

    @Test
    fun `create memory saves node and marks cell memorized`() = runTest {
        val memories = FakeMemoryRepository()
        val exploration = FakeExplorationRepository()
        val footprints = FakeFootprintRepository()
        val useCase = CreateMemoryUseCase(grid, clock, memories, exploration, footprints)

        val coord = WorldCoordinate(39.7326, 116.1712)
        val cell = grid.cellOf(coord)
        val node = useCase(MemoryDraft(coordinate = coord, text = "第一次在雨中来到湖边", mood = Mood.CALM))

        assertEquals(1, memories.saved.size)
        assertEquals("第一次在雨中来到湖边", memories.saved.first().text)
        assertEquals(cell, node.cellId)
        assertEquals(CellFogState.MEMORIZED, exploration.state.stateOf(cell))
        assertEquals(1, footprints.events.size)
        assertEquals(FootprintEventType.MEMORY_CREATED, footprints.events.first().eventType)
    }

    @Test
    fun `memory with photo stores media ref`() = runTest {
        val memories = FakeMemoryRepository()
        val useCase = CreateMemoryUseCase(
            grid, clock, memories, FakeExplorationRepository(), FakeFootprintRepository(),
        )
        val coord = WorldCoordinate(39.7326, 116.1712)
        val node = useCase(
            MemoryDraft(
                coordinate = coord,
                text = "镜月鱼影",
                media = CapturedMedia(localUri = "file:///x.jpg", capturedAtEpochMs = 123L),
            ),
        )
        assertEquals(listOf("file:///x.jpg"), node.mediaRefs)
    }

    @Test
    fun `memorized does not downgrade special cell`() = runTest {
        val exploration = FakeExplorationRepository()
        val coord = WorldCoordinate(39.7326, 116.1712)
        val cell = grid.cellOf(coord)
        exploration.saveStates(mapOf(cell to CellFogState.SPECIAL))

        val useCase = CreateMemoryUseCase(
            grid, clock, FakeMemoryRepository(), exploration, FakeFootprintRepository(),
        )
        useCase(MemoryDraft(coordinate = coord, text = "hi"))

        // SPECIAL 比 MEMORIZED 更高，应保留 SPECIAL
        assertEquals(CellFogState.SPECIAL, exploration.state.stateOf(cell))
    }

    @Test
    fun `draft validity requires text or media`() {
        val coord = WorldCoordinate(39.7326, 116.1712)
        assertTrue(!MemoryDraft(coord).isValid)
        assertTrue(MemoryDraft(coord, text = "x").isValid)
        assertTrue(
            MemoryDraft(coord, media = CapturedMedia("u", 1L)).isValid,
        )
    }
}
