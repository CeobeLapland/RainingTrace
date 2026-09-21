package com.rainingtrace.domain.npc

import kotlinx.coroutines.flow.Flow

/**
 * NPC 消息仓储。
 *
 * 会话列表用 [observeConversations]（已在实现里按 npcId 分组好），
 * 未读总数给底栏红点，线程给聊天页。
 */
interface NpcMessageRepository {
    /** 会话列表：每个 NPC 的最后一条消息与未读数，按最后消息时间倒序。 */
    fun observeConversations(): Flow<List<NpcConversation>>

    /** 未读总数（只算 NPC 发来的）。 */
    fun observeUnreadCount(): Flow<Int>

    /** 某个 NPC 的最近 [limit] 条，时间升序。 */
    fun observeThread(npcId: String, limit: Int = DEFAULT_THREAD_LIMIT): Flow<List<NpcMessage>>

    /** 一次性读取（给 UseCase 拼上下文用，不是 UI 订阅）。 */
    suspend fun recent(npcId: String, limit: Int = DEFAULT_THREAD_LIMIT): List<NpcMessage>

    suspend fun append(message: NpcMessage)

    /** 打开会话时把该 NPC 的未读清掉。 */
    suspend fun markRead(npcId: String)

    companion object {
        const val DEFAULT_THREAD_LIMIT = 50
    }
}
