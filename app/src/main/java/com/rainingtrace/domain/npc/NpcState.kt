package com.rainingtrace.domain.npc

/**
 * NPC 情绪。
 *
 * 与 [com.rainingtrace.domain.memory.Mood]（**记忆的心情**）语义不同，不要混用。
 *
 * 设计要点：**基线情绪不落库**，由世界状态与"他此刻在不在路上"派生
 * （见 `NpcAffection.baselineMoodOf`）；库里只存"事件情绪 + 起始时刻"。
 * 这样情绪会自己淡回基线，却不需要任何定时器或 tick——纯函数即可测。
 */
enum class NpcMood(val label: String) {
    CALM("平静"),
    GLAD("高兴"),
    INTRIGUED("好奇"),
    TIRED("疲倦"),

    /** 正在赶路（走路时被搭话）。 */
    BUSY("忙碌"),
    DOWN("低落"),
}

/**
 * 玩家与某个 NPC 的关系阶段，由 [NpcState.affection] 派生。
 *
 * 只存一条好感数值：`NPC_MET` 足迹只记第一次（见 `RecordNpcEncounterUseCase`），
 * 派不出"熟悉度"；再加一条数值只会互相漂移，玩家也感知不到区别。
 */
enum class RelationshipStage(val label: String) {
    STRANGER("还没说过话"),
    NODDING("点头之交"),
    ACQUAINTED("熟识"),
    FRIEND("朋友"),
    ;

    companion object {
        const val NODDING_FROM = 10
        const val ACQUAINTED_FROM = 30
        const val FRIEND_FROM = 70

        fun of(affection: Int): RelationshipStage = when {
            affection >= FRIEND_FROM -> FRIEND
            affection >= ACQUAINTED_FROM -> ACQUAINTED
            affection >= NODDING_FROM -> NODDING
            else -> STRANGER
        }
    }
}

/**
 * 玩家与某个 NPC 之间**可变**的状态（每个 NPC 一行）。
 *
 * 见过的次数、上次在哪遇见，都不存在这里——那些从 `NPC_MET` / `NPC_TALKED`
 * 足迹事件派生（项目原则：状态与事件分离，不存第二份真相）。
 */
data class NpcState(
    val npcId: String,
    val affection: Int = 0,
    /** 最近一次**事件**情绪；过一段时间会淡回基线，基线不入库。 */
    val mood: NpcMood = NpcMood.CALM,
    val moodSinceEpochMs: Long = 0L,
    /** 上次聊天时间；主动消息的"最近没见你"用它，避免扫全量足迹。 */
    val lastInteractionAtEpochMs: Long? = null,
    /** 今日已涨的好感与它属于哪一天：日上限的 O(1) 记账。 */
    val todayAffectionGain: Int = 0,
    val todayDateKey: String = "",
    val updatedAtEpochMs: Long = 0L,
) {
    val stage: RelationshipStage get() = RelationshipStage.of(affection)

    companion object {
        /** 还没打过交道的人：不落库，首次变化才写。 */
        fun initial(npcId: String): NpcState = NpcState(npcId = npcId)
    }
}
