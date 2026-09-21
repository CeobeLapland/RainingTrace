package com.rainingtrace.domain.npc

import kotlinx.coroutines.flow.Flow

/**
 * NPC 关系/情绪状态仓储（可变状态，每个 NPC 一行）。
 *
 * [stateOf] 对没打过交道的人返回 [NpcState.initial]，**首次变化才落库**——
 * 不给每个 NPC 预写一行空状态。
 */
interface NpcStateRepository {
    /** 所有已有状态的 NPC（没打过交道的不会出现在这里）。 */
    fun observeStates(): Flow<Map<String, NpcState>>

    suspend fun stateOf(npcId: String): NpcState

    suspend fun save(state: NpcState)
}
