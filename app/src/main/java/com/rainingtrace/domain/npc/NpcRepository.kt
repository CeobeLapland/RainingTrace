package com.rainingtrace.domain.npc

/**
 * NPC 档案仓储。
 *
 * **刻意不提供 `nearby`**：NPC 的位置是时间的函数（见 [ResolvedSchedule.presenceAt]），
 * "按半径查附近的人"在语义上是错的——那个人此刻可能正在半路上。
 */
interface NpcRepository {
    suspend fun all(): List<NpcProfile>

    suspend fun byId(id: String): NpcProfile?
}
