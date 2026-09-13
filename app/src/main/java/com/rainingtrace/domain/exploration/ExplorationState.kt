package com.rainingtrace.domain.exploration

import com.rainingtrace.domain.map.HexCellId

/**
 * RT-MAP-004: 迷雾/探索状态。
 *
 * 状态语义（见 06_地图专项 §3）：
 * - UNKNOWN：未揭示
 * - DISCOVERED：从别处"看到"过（地图揭开），但人没到过
 * - VISITED：玩家真正到过
 * - MEMORIZED：留下过记忆节点
 * - SPECIAL：异常/事件等特殊标记
 *
 * "地图发现"与"玩家到过"不允许混为一个 bool。
 */
enum class CellFogState {
    UNKNOWN,
    DISCOVERED,
    VISITED,
    MEMORIZED,
    SPECIAL,
}

/**
 * 玩家视角的探索状态投影。
 *
 * 只保存"已知"的单元；UNKNOWN 通过缺省表达。
 * 状态只升不降（reveal 不会把 VISITED 降级回 DISCOVERED）。
 */
data class ExplorationState(
    val cellStates: Map<HexCellId, CellFogState> = emptyMap(),
) {
    fun stateOf(cell: HexCellId): CellFogState = cellStates[cell] ?: CellFogState.UNKNOWN

    fun revealedCount(): Int = cellStates.size

    /** 用新状态覆盖；仅当新状态等级 >= 旧状态时生效。 */
    fun withState(cell: HexCellId, newState: CellFogState): ExplorationState {
        val current = stateOf(cell)
        return if (newState.rank >= current.rank) {
            copy(cellStates = cellStates + (cell to newState))
        } else {
            this
        }
    }

    private val CellFogState.rank: Int
        get() = when (this) {
            CellFogState.UNKNOWN -> 0
            CellFogState.DISCOVERED -> 1
            CellFogState.VISITED -> 2
            CellFogState.MEMORIZED -> 3
            CellFogState.SPECIAL -> 4
        }
}
