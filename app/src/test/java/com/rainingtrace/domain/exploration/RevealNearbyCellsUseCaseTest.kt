package com.rainingtrace.domain.exploration

import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test

class RevealNearbyCellsUseCaseTest {

    private val grid = HexGrid(WorldCoordinate(39.7310, 116.1711), cellSizeMeters = 80.0)
    private val reveal = RevealNearbyCellsUseCase(grid)
    private val visit = MarkCellVisitedUseCase()

    @Test
    fun `reveal radius zero marks center discovered`() {
        val result = reveal(ExplorationState(), HexCellId(0, 0), 0)
        assertEquals(CellFogState.DISCOVERED, result.stateOf(HexCellId(0, 0)))
        assertEquals(1, result.revealedCount())
    }

    @Test
    fun `reveal radius one marks seven discovered`() {
        val result = reveal(ExplorationState(), HexCellId(0, 0), 1)
        assertEquals(7, result.revealedCount())
        result.cellStates.values.forEach { assertEquals(CellFogState.DISCOVERED, it) }
    }

    @Test
    fun `reveal does not downgrade visited cell`() {
        val start = visit(ExplorationState(), HexCellId(0, 0))
        val result = reveal(start, HexCellId(0, 0), 1)
        assertEquals(CellFogState.VISITED, result.stateOf(HexCellId(0, 0)))
        assertEquals(7, result.revealedCount())
    }

    @Test
    fun `reveal is idempotent`() {
        val once = reveal(ExplorationState(), HexCellId(2, -1), 2)
        val twice = reveal(once, HexCellId(2, -1), 2)
        assertEquals(once, twice)
    }

    @Test
    fun `visit then reveal then visit keeps visited`() {
        val cell = HexCellId(4, 4)
        var state = reveal(ExplorationState(), cell, 1)
        state = visit(state, cell)
        state = reveal(state, cell, 1)
        assertEquals(CellFogState.VISITED, state.stateOf(cell))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative reveal radius rejected`() {
        reveal(ExplorationState(), HexCellId(0, 0), -1)
    }

    @Test
    fun `unknown cell defaults to unknown state`() {
        assertEquals(CellFogState.UNKNOWN, ExplorationState().stateOf(HexCellId(9, 9)))
    }
}
