package com.rainingtrace.domain.track

import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.WorldCoordinate

/**
 * 战争迷雾：以一个真实轨迹点为圆心，按**米制半径**开雾。
 *
 * - [SIGHT_RADIUS_METERS] 内：DISCOVERED（看见过，人未必到过）；
 * - [VISIT_RADIUS_METERS] 内：VISITED（真正到过）；
 * - 状态只升不降（MEMORIZED/SPECIAL 不会被覆盖，见 ExplorationState.withState）。
 *
 * 六边形只是表现网格；输入是连续坐标，半径与格子尺寸无关。
 */
class RevealFogFromPointUseCase(
    private val grid: HexGrid,
) {
    operator fun invoke(
        state: ExplorationState,
        coordinate: WorldCoordinate,
    ): ExplorationState {
        val sighted = grid.cellsWithinMeters(coordinate, SIGHT_RADIUS_METERS)
        val afterSight = sighted.fold(state) { acc, cell ->
            acc.withState(cell, CellFogState.DISCOVERED)
        }
        val visited = grid.cellsWithinMeters(coordinate, VISIT_RADIUS_METERS)
        return visited.fold(afterSight) { acc, cell ->
            acc.withState(cell, CellFogState.VISITED)
        }
    }

    companion object {
        const val VISIT_RADIUS_METERS = 30.0
        const val SIGHT_RADIUS_METERS = 60.0
    }
}

/**
 * 从全部轨迹点重建迷雾（切换格子档位时使用）。
 * 迷雾是轨迹点的物化投影：轨迹点在，迷雾随时可以重算，幂等。
 */
class RebuildFogFromTrackUseCase(
    private val reveal: RevealFogFromPointUseCase,
) {
    operator fun invoke(points: List<TrackPoint>): ExplorationState {
        var state = ExplorationState()
        points.forEach { point ->
            state = reveal(state, point.coordinate)
        }
        return state
    }
}
