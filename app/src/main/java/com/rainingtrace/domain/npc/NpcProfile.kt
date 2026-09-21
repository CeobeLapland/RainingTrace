package com.rainingtrace.domain.npc

/**
 * NPC 档案（GDD §14）。
 *
 * 和 [com.rainingtrace.domain.map.Place] 一样是**只读配置**：手工编写、不落库，
 * 将来由服务端/内容资产下发。玩家相关的可变状态（好感、情绪、承诺）属于后续切片，
 * 会另开存储，不要塞进这里。
 */
data class NpcProfile(
    val id: String,
    val name: String,
    /** 一句话人设，卡片上显示。 */
    val oneLiner: String,
    /** 作息表；可以只有一条（整天待在同一个地方）。 */
    val schedule: List<NpcScheduleEntry>,
    /** 身份，例如"大二学生""食堂帮工"；主动消息与第三人称指代会用到。 */
    val role: String = "",
    /** 性格标签：驱动语气与偏好。 */
    val traits: Set<NpcTrait> = emptySet(),
    /** 愿意聊的话题。 */
    val topics: Set<NpcTopic> = emptySet(),
    /** 最喜欢的话题（好感加成与专属台词）；必须也是 [topics] 之一。 */
    val favoriteTopic: NpcTopic? = null,
    /** 他自己的经历片段（1~3 条），作为"自述"素材池。 */
    val backstory: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "npc id must not be blank" }
        require(name.isNotBlank()) { "npc name must not be blank" }
        // 内容写错时不该静默失效（专属台词会永远不出现），直接拦在构造期。
        require(favoriteTopic == null || favoriteTopic in topics) {
            "favoriteTopic must be one of topics: $favoriteTopic not in $topics"
        }
    }
}

/**
 * 一条作息：**[startMinute] 是"到达 [placeId]"的时刻**。
 *
 * - 条目生效区间是 `[startMinute_i, startMinute_{i+1})`（按天环，见 [ResolvedSchedule.presenceAt]）；
 * - 行走发生在 `[startMinute_i - travelMinutes, startMinute_i)`，
 *   从**上一条**作息的地点走到本条的地点；
 * - [travelMinutes] 只表达"路上要多久"，不表达路径——本作的路点+直线插值方案
 *   不做寻路（见 handoff 的设计结论）。
 */
data class NpcScheduleEntry(
    /** 到达时刻，本地当天第几分钟（0..1439）。 */
    val startMinute: Int,
    val placeId: String,
    val travelMinutes: Int = 0,
    /** 此刻在做什么，纯文案（不造枚举，避免过早抽象）。 */
    val activity: String = "",
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range: $startMinute" }
        require(travelMinutes >= 0) { "travelMinutes must not be negative: $travelMinutes" }
    }
}

internal const val MINUTES_PER_DAY = 24 * 60
