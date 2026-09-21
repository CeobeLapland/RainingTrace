package com.rainingtrace.data.repository

import com.rainingtrace.data.local.NpcCommitmentDao
import com.rainingtrace.data.local.NpcCommitmentEntity
import com.rainingtrace.data.local.NpcMessageDao
import com.rainingtrace.data.local.NpcMessageEntity
import com.rainingtrace.data.local.NpcStateDao
import com.rainingtrace.data.local.NpcStateEntity
import com.rainingtrace.domain.npc.NpcCommitment
import com.rainingtrace.domain.npc.NpcCommitmentRepository
import com.rainingtrace.domain.npc.NpcCommitmentStatus
import com.rainingtrace.domain.npc.NpcConversation
import com.rainingtrace.domain.npc.NpcMessage
import com.rainingtrace.domain.npc.NpcMessageRepository
import com.rainingtrace.domain.npc.NpcMessageSource
import com.rainingtrace.domain.npc.NpcMessageSpeaker
import com.rainingtrace.domain.npc.NpcMood
import com.rainingtrace.domain.npc.NpcState
import com.rainingtrace.domain.npc.NpcStateRepository
import com.rainingtrace.domain.npc.NpcTopic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * NPC 消息的 Room 实现。
 *
 * 会话列表**刻意在领域侧分组**：只订阅一个 `observeAll()` Flow，
 * 没有"两次查询之间失效导致漏报"的风险。NPC 只有几个、消息量级在数百条，
 * 等真到万级再改成 SQL 侧 GROUP BY 汇总。
 */
class RoomNpcMessageRepository(
    private val dao: NpcMessageDao,
) : NpcMessageRepository {

    override fun observeConversations(): Flow<List<NpcConversation>> =
        dao.observeAll().map { rows ->
            rows.map { it.toDomain() }
                .groupBy { it.npcId }
                .map { (npcId, thread) ->
                    NpcConversation(
                        npcId = npcId,
                        lastMessage = thread.maxByOrNull { it.createdAtEpochMs },
                        unreadCount = thread.count { it.fromNpc && !it.read },
                    )
                }
                .sortedByDescending { it.lastMessage?.createdAtEpochMs ?: Long.MIN_VALUE }
        }

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override fun observeThread(npcId: String, limit: Int): Flow<List<NpcMessage>> =
        dao.observeThread(npcId, limit).map { rows -> rows.map { it.toDomain() }.reversed() }

    override suspend fun recent(npcId: String, limit: Int): List<NpcMessage> =
        dao.observeThread(npcId, limit).first().map { it.toDomain() }.reversed()

    override suspend fun append(message: NpcMessage) {
        dao.insert(message.toEntity())
    }

    override suspend fun markRead(npcId: String) {
        dao.markRead(npcId)
    }

    private fun NpcMessageEntity.toDomain() = NpcMessage(
        id = id,
        npcId = npcId,
        speaker = NpcMessageSpeaker.valueOf(speaker),
        text = text,
        createdAtEpochMs = createdAtEpochMs,
        read = isRead,
        source = NpcMessageSource.valueOf(source),
        topic = topic?.let { runCatching { NpcTopic.valueOf(it) }.getOrNull() },
        ruleId = ruleId,
    )

    private fun NpcMessage.toEntity() = NpcMessageEntity(
        id = id,
        npcId = npcId,
        speaker = speaker.name,
        text = text,
        createdAtEpochMs = createdAtEpochMs,
        isRead = read,
        source = source.name,
        topic = topic?.name,
        ruleId = ruleId,
    )
}

/** NPC 关系/情绪状态的 Room 实现。 */
class RoomNpcStateRepository(
    private val dao: NpcStateDao,
) : NpcStateRepository {

    override fun observeStates(): Flow<Map<String, NpcState>> =
        dao.observeAll().map { rows -> rows.associate { it.npcId to it.toDomain() } }

    override suspend fun stateOf(npcId: String): NpcState =
        dao.byId(npcId)?.toDomain() ?: NpcState.initial(npcId)

    override suspend fun save(state: NpcState) {
        dao.upsert(state.toEntity())
    }

    private fun NpcStateEntity.toDomain() = NpcState(
        npcId = npcId,
        affection = affection,
        mood = runCatching { NpcMood.valueOf(mood) }.getOrDefault(NpcMood.CALM),
        moodSinceEpochMs = moodSinceEpochMs,
        lastInteractionAtEpochMs = lastInteractionAtEpochMs,
        todayAffectionGain = todayAffectionGain,
        todayDateKey = todayDateKey,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun NpcState.toEntity() = NpcStateEntity(
        npcId = npcId,
        affection = affection,
        mood = mood.name,
        moodSinceEpochMs = moodSinceEpochMs,
        lastInteractionAtEpochMs = lastInteractionAtEpochMs,
        todayAffectionGain = todayAffectionGain,
        todayDateKey = todayDateKey,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

/** 约定的 Room 实现。 */
class RoomNpcCommitmentRepository(
    private val dao: NpcCommitmentDao,
) : NpcCommitmentRepository {

    override suspend fun all(): List<NpcCommitment> = dao.all().map { it.toDomain() }

    override suspend fun save(commitment: NpcCommitment) {
        dao.upsert(
            NpcCommitmentEntity(
                id = commitment.id,
                npcId = commitment.npcId,
                placeId = commitment.placeId,
                dateKey = commitment.dateKey,
                startMinute = commitment.startMinute,
                endMinute = commitment.endMinute,
                travelMinutes = commitment.travelMinutes,
                status = commitment.status.name,
                createdAtEpochMs = commitment.createdAtEpochMs,
                resolvedAtEpochMs = commitment.resolvedAtEpochMs,
            ),
        )
    }

    private fun NpcCommitmentEntity.toDomain() = NpcCommitment(
        id = id,
        npcId = npcId,
        placeId = placeId,
        dateKey = dateKey,
        startMinute = startMinute,
        endMinute = endMinute,
        travelMinutes = travelMinutes,
        // 状态名读不出来时当成已失效，别让它永远挂在"等待中"。
        status = runCatching { NpcCommitmentStatus.valueOf(status) }
            .getOrDefault(NpcCommitmentStatus.MISSED),
        createdAtEpochMs = createdAtEpochMs,
        resolvedAtEpochMs = resolvedAtEpochMs,
    )
}
