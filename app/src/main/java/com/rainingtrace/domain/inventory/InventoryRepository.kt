package com.rainingtrace.domain.inventory

import kotlinx.coroutines.flow.Flow

/** 库存仓库：当前真相；MVP 本地 Room，P1 服务端确认后回写。 */
interface InventoryRepository {
    fun observeState(): Flow<InventoryState>

    suspend fun loadState(): InventoryState

    suspend fun saveState(state: InventoryState)
}
