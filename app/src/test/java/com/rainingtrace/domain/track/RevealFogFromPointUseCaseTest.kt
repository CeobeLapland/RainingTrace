package com.rainingtrace.domain.track

import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test

class RevealFogFromPointUseCaseTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)
    private val grid = HexGrid(origin, cellSizeMeters = 40.0)
    private val reveal = RevealFogFromPointUseCase(grid)

    @Test
    fun `origin cell visited at origin`() {
        val state = reveal(ExplorationState(), origin)
        assertEquals(CellFogState.VISITED, state.stateOf(HexCellId(0, 0)))
    }

    @Test
    fun `sight circle discovered but not visited`() {
        // 邻格与 60m 视野圆相交（格缝 34.6m），但与 30m 到达圈不相交
        val state = reveal(ExplorationState(), origin)
        assertEquals(CellFogState.DISCOVERED, state.stateOf(HexCellId(1, 0)))
        assertEquals(CellFogState.DISCOVERED, state.stateOf(HexCellId(0, -1)))
    }

    @Test
    fun `cells beyond sight stay unknown`() {
        // 两环格最近点 80m，超出 60m 视野
        val state = reveal(ExplorationState(), origin)
        assertEquals(CellFogState.UNKNOWN, state.stateOf(HexCellId(0, -2)))
        assertEquals(CellFogState.UNKNOWN, state.stateOf(HexCellId(2, 0)))
    }

    @Test
    fun `cell containing the point is always visited even near vertex`() {
        // 向北 50m：人站的格子即使格中心超出 30m 到达圈，也必须 VISITED。
        val north50 = WorldCoordinate(origin.latDegrees + 50.0 / 111_320.0, origin.lngDegrees)
        val occupiedCell = grid.cellOf(north50)
        val state = reveal(ExplorationState(), north50)
        assertEquals(CellFogState.VISITED, state.stateOf(occupiedCell))
    }

    @Test
    fun `memorized cell is not downgraded`() {
        val seeded = ExplorationState(
            mapOf(HexCellId(0, 0) to CellFogState.MEMORIZED),
        )
        val state = reveal(seeded, origin)
        assertEquals(CellFogState.MEMORIZED, state.stateOf(HexCellId(0, 0)))
    }
}

class RebuildFogFromTrackUseCaseTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)
    private val grid = HexGrid(origin, cellSizeMeters = 40.0)

    private fun point(latOffsetMeters: Double, ts: Long) = TrackPoint(
        id = "p$ts",
        timestampEpochMs = ts,
        coordinate = WorldCoordinate(origin.latDegrees + latOffsetMeters / 111_320.0, origin.lngDegrees),
        accuracyMeters = 10.0,
        source = LocationSource.FAKE,
    )

    @Test
    fun `rebuild from same points is idempotent`() {
        val reveal = RevealFogFromPointUseCase(grid)
        val rebuild = RebuildFogFromTrackUseCase(reveal)
        val points = listOf(point(0.0, 1L), point(40.0, 2L), point(90.0, 3L))

        val first = rebuild(points)
        val second = rebuild(points)

        assertEquals(first.cellStates, second.cellStates)
        assertEquals(CellFogState.VISITED, first.stateOf(HexCellId(0, 0)))
    }

    @Test
    fun `empty track produces empty fog`() {
        val rebuild = RebuildFogFromTrackUseCase(RevealFogFromPointUseCase(grid))
        assertEquals(0, rebuild(emptyList()).revealedCount())
    }
}
