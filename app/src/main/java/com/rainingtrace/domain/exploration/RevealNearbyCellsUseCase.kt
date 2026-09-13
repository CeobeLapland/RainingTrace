package com.rainingtrace.domain.exploration

import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.HexGrid

/**
 * RT-MAP-005: 揭示玩家附近六边形单元。
 *
 * 输入：玩家所在 cell + 揭示半径。
 * 输出：更新后的 [ExplorationState]（UNKNOWN → DISCOVERED，不降级已有更强状态）。
 *
 * 非目标（本切片）：真实 GPS、后端同步、动画。
 */
class RevealNearbyCellsUseCase(
    private val grid: HexGrid,
) {
    operator fun invoke(
        state: ExplorationState,
        playerCell: HexCellId,
        revealRadius: Int,
    ): ExplorationState {
        require(revealRadius >= 0) { "revealRadius must be >= 0, got $revealRadius" }
        val cells = grid.cellsWithinRadius(playerCell, revealRadius)
        return cells.fold(state) { acc, cell ->
            acc.withState(cell, CellFogState.DISCOVERED)
        }
    }
}

/**
 * 玩家真正到达某格：DISCOVERED/UNKNOWN → VISITED。
 * 与 reveal 分离，避免"看到"与"到过"混淆。
 */
class MarkCellVisitedUseCase {
    operator fun invoke(
        state: ExplorationState,
        cell: HexCellId,
    ): ExplorationState = state.withState(cell, CellFogState.VISITED)
}
