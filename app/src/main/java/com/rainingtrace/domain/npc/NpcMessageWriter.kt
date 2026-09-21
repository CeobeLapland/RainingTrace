package com.rainingtrace.domain.npc

import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.WorldCoordinate
import java.util.UUID

/**
 * "NPC 主动说了一句话"的共用写法：写消息 + 写足迹。
 *
 * 片 2（主动消息）与片 3（约定兑现）都走这里——两者的差别只在足迹类型与 payload，
 * 而"消息要未读、足迹要留档"这件事必须一致，否则未读红点或去重会有一边坏掉。
 */
class NpcMessageWriter(
    private val messageRepository: NpcMessageRepository,
    private val footprintRepository: FootprintRepository,
) {

    suspend fun write(
        npcId: String,
        coordinate: WorldCoordinate,
        text: String,
        /** 留档的事件类型（主动消息是 NPC_MESSAGE_SENT，约定是 KEPT/MISSED）。 */
        eventType: FootprintEventType,
        /** 正文之外要留档的键（如 ruleId / commitmentId）。 */
        payload: Map<String, String>,
        nowEpochMs: Long,
    ): NpcMessage {
        val message = NpcMessage(
            id = UUID.randomUUID().toString(),
            npcId = npcId,
            speaker = NpcMessageSpeaker.NPC,
            text = text,
            createdAtEpochMs = nowEpochMs,
            read = false,
            source = NpcMessageSource.TEMPLATE,
            ruleId = payload["ruleId"],
        )
        messageRepository.append(message)
        footprintRepository.append(
            FootprintEvent(
                id = UUID.randomUUID().toString(),
                timestampEpochMs = nowEpochMs,
                coordinate = coordinate,
                eventType = eventType,
                payload = payload + mapOf("npcId" to npcId),
            ),
        )
        return message
    }
}