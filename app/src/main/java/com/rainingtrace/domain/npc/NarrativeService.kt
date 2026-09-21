package com.rainingtrace.domain.npc

import com.rainingtrace.domain.world.WorldState

/**
 * 生成 NPC 的回复文本。
 *
 * 命名与职责对齐 `doc/04_AI提示词_Prompt_Pack.md` 的 Prompt 08：
 * **AI 只产候选文本，NPC 状态由 domain 决定**。所以这个接口只返回文本，
 * 不返回任何"好感变化""位置变化"——那些由 [SendNpcMessageUseCase] 在调用它**之前**
 * 用纯函数算好。将来接 LLM 只需换一个实现。
 */
interface NarrativeService {
    suspend fun respond(context: NpcDialogueContext): GeneratedDialogue
}

/** 生成回复所需的全部上下文（一次性给全，实现不必再去查库）。 */
data class NpcDialogueContext(
    val profile: NpcProfile,
    val state: NpcState,
    val stage: RelationshipStage,
    val mood: NpcMood,
    val presence: NpcPresence?,
    val parsed: ParsedPlayerMessage,
    val worldState: WorldState,
    /** 最近的几条消息，用来避免同一句话连续出现。 */
    val recentMessages: List<NpcMessage> = emptyList(),
    /** 玩家问到"某个时刻"时的真实作息；没问就是 null。 */
    val scheduleFacts: ScheduleFacts? = null,
    /**
     * 这次对话涉及的一次约定（片 3）。
     * [CommitmentFacts.agreed] = true 时**已经写进库了**，所以承诺词可以说；
     * false 时只能说软拒绝。
     */
    val commitmentFacts: CommitmentFacts? = null,
    /** "他记得你"的句子素材（可能为空）。 */
    val memoryHooks: List<String> = emptyList(),
)

data class GeneratedDialogue(
    val text: String,
    val source: NpcMessageSource = NpcMessageSource.TEMPLATE,
    val topic: NpcTopic? = null,
)

/**
 * 回复文本的守门人。
 *
 * 片 1 没有承诺系统，所以**说了"等你"却不去就是骗人**——这里机械挡掉承诺词。
 * 片 3 之后承诺可以说了，但只有在 [allowPromises] 为真时才放行，
 * 而调用方只有**已经把承诺写进库**才会传 true。所以"不骗人"不靠自觉，靠这一层。
 *
 * 注意：AI 生成的文本也过这里，所以将来接 LLM 时同样受约束（Prompt 08）。
 */
object DialogueValidator {

    /** 一条消息的上限；再长就不像聊天了。 */
    const val MAX_LENGTH = 120

    /** 承诺词：只有在 [validate] 的 allowPromises 为真时才允许出现。 */
    private val PROMISE_WORDS = listOf("等你", "我一定", "答应你", "说好了", "不见不散", "保证")

    /** 回落台词：短、中性、不承诺。 */
    private val FALLBACK_LINES = listOf("嗯，我听着。", "……", "你说。", "嗯。")

    fun validate(text: String, allowPromises: Boolean = false): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return false
        if ('{' in trimmed || '}' in trimmed) return false
        if (allowPromises) return true
        return PROMISE_WORDS.none { it in trimmed }
    }

    /** 确定性回落：避开最近用过的句子。 */
    fun fallback(recentTexts: Collection<String>): String =
        FALLBACK_LINES.firstOrNull { it !in recentTexts } ?: FALLBACK_LINES.first()
}
