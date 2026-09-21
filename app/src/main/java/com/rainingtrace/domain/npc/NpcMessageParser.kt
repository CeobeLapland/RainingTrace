package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.world.WorldState

/** 解析一句话所需的上下文；也是给将来 AI 实现留的口子。 */
data class ParseContext(
    val npc: NpcProfile,
    val places: List<Place>,
    /** 口语别名 → placeId（"湖边" → 北湖），由内容侧维护。 */
    val placeAliases: Map<String, String>,
    val worldState: WorldState,
    val presence: NpcPresence?,
)

/**
 * 玩家一句话解析出来的结构化意图。
 *
 * [wantsToMeet] 与 [asksAboutSchedule] **必须分开**：
 * "明天在图书馆等我"是前者（片 1 不会答应），"明天下午你在哪"是后者（可以如实回答）。
 */
data class ParsedPlayerMessage(
    val raw: String,
    val topics: Set<NpcTopic> = emptySet(),
    val mentionedPlaceId: String? = null,
    /** 提到地点的正式名（台词里要念出来，所以解析时就带上）。 */
    val mentionedPlaceName: String? = null,
    val timeHint: TimeHint? = null,
    val wantsToMeet: Boolean = false,
    val asksAboutSchedule: Boolean = false,
    val isQuestion: Boolean = false,
    val confidence: Double = 0.0,
) {
    /** 听懂了多少：决定要不要给好感、要不要走"没听懂"分支。 */
    val understood: Boolean
        get() = topics.isNotEmpty() || mentionedPlaceId != null || timeHint != null ||
            wantsToMeet || asksAboutSchedule
}

/**
 * 把玩家消息解析成结构化意图。
 *
 * 接口先于实现：规则版现在够用，将来接 AI 时只需换实现——
 * **AI 可以给出"动作与地点"的候选，但状态与规则仍由 domain 决定**（Prompt 08）。
 *
 * 实现必须**永不抛异常**：解析不出来就返回 `confidence = 0`，
 * 由叙事层走"没听懂"分支，绝不假装听懂。
 */
interface NpcMessageParser {
    suspend fun parse(text: String, context: ParseContext): ParsedPlayerMessage
}
