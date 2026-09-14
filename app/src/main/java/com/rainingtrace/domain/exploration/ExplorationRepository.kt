package com.rainingtrace.domain.exploration

import com.rainingtrace.domain.map.HexCellId
import kotlinx.coroutines.flow.Flow

/** 探索状态仓库：当前真相 + 可恢复。 */
interface ExplorationRepository {
    fun observeState(): Flow<ExplorationState>

    suspend fun loadState(): ExplorationState

    suspend fun saveStates(states: Map<HexCellId, CellFogState>)

    /** 清空当前格子档位的全部迷雾（切换档位后重建前调用）。 */
    suspend fun clearLevel()
}
