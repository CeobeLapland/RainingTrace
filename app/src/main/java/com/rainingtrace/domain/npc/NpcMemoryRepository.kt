package com.rainingtrace.domain.npc

/** NPC 记忆的存取。数据层（Room）实现；domain 只依赖接口。 */
interface NpcMemoryRepository {
    suspend fun factsFor(npcId: String): List<NpcMemoryFact>

    /** 全部记忆（清理/统计用；量级小）。 */
    suspend fun all(): List<NpcMemoryFact>

    suspend fun append(fact: NpcMemoryFact)

    /** 编辑已有记忆（整行覆盖）。 */
    suspend fun upsert(fact: NpcMemoryFact)

    suspend fun remove(ids: List<String>)

    /** 这些事实被用进过 prompt：涨引用计数、刷新鲜度。 */
    suspend fun touch(npcId: String, ids: List<String>, nowEpochMs: Long)
}
