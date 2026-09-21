package com.rainingtrace.domain.npc

/** 消息是谁说的。 */
enum class NpcMessageSpeaker {
    PLAYER,
    NPC,
}

/**
 * 消息的来源。
 *
 * [TEMPLATE] 与 [AI] 的区分是给将来的 LLM 留的口子（见 Prompt 08）：
 * AI 只产候选文本，状态与规则一律由 domain 决定，所以这里只记录"这句是谁写的"。
 */
enum class NpcMessageSource {
    PLAYER,
    TEMPLATE,
    AI,
}

/** 一条消息。append-only，不修改（未读状态除外）。 */
data class NpcMessage(
    val id: String,
    val npcId: String,
    val speaker: NpcMessageSpeaker,
    val text: String,
    val createdAtEpochMs: Long,
    val read: Boolean,
    val source: NpcMessageSource,
    /** 解析出的话题（若识别到），用于留档与台词去重。 */
    val topic: NpcTopic? = null,
    /** 主动消息是哪条规则发的（可追溯）。 */
    val ruleId: String? = null,
) {
    val fromNpc: Boolean get() = speaker == NpcMessageSpeaker.NPC
}

/** 会话列表的一行：某个 NPC 的最后一条消息与未读数。 */
data class NpcConversation(
    val npcId: String,
    val lastMessage: NpcMessage?,
    val unreadCount: Int,
)
