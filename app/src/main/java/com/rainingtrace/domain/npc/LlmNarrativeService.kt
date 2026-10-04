package com.rainingtrace.domain.npc

import com.rainingtrace.domain.world.WorldState

/**
 * LLM 版叙事：组装"人格 + 状态 + 记忆 + 最近消息"的有界 prompt，调 [LlmClient]，
 * 失败或不合规就回退到模板叙事。
 *
 * 设计约束（沿用 Prompt 08 的"AI 只产文本，状态由 domain 决定"）：
 * - 返回的只是候选文本，仍由 `SendNpcMessageUseCase` 算好感/情绪/承诺；
 * - 文本仍过 `DialogueValidator`（承诺词只在真答应后才放行）；
 * - LLM 不可用（client 为 null / 返回 null / 抛异常）→ 模板兜底，**绝不假装听懂**。
 */
class LlmNarrativeService(
    private val client: LlmClient?,
    private val fallback: NarrativeService,
    private val memoryRepository: NpcMemoryRepository? = null,
    private val personaPrompt: (NpcProfile) -> String = NpcPersonaPrompt::personaFor,
    /** 测试可注入固定时间；默认真实时钟。 */
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : NarrativeService {

    override suspend fun respond(context: NpcDialogueContext): GeneratedDialogue {
        val llm = client ?: return fallback.respond(context)
        val memories = memoryRepository?.factsFor(context.profile.id).orEmpty()
        val topFacts = NpcMemoryMaintenance.topK(memories)

        val trimmed = PromptBudget.trimToBudget(
            persona = personaPrompt(context.profile),
            memoryLines = topFacts.map { "记忆：${it.content}" },
            recentMessages = context.recentMessages
                .map { if (it.fromNpc) "${context.profile.name}：${it.text}" else "玩家：${it.text}" },
        )

        // 用过的记忆刷一下新鲜度（下次挑选时"最近被想起"算数）
        if (topFacts.isNotEmpty() && memoryRepository != null) {
            memoryRepository.touch(context.profile.id, topFacts.map { it.id }, nowEpochMs())
        }

        val system = buildString {
            append(trimmed.persona).append("\n\n")
            append(stateBlock(context))
            if (trimmed.memory.isNotEmpty()) append("\n").append(trimmed.memory.joinToString("\n"))
            append("\n\n你正在和玩家聊天。请用一句话以内的中文回答，符合你的人设，不要说破自己是 AI。")
            if (context.commitmentFacts?.agreed == true) {
                append("你们约好${context.commitmentFacts.timeLabel}在「${context.commitmentFacts.placeName}」见面，可以说准。")
            }
        }
        val turns = trimmed.recent.map { text ->
            val role = if (text.startsWith("玩家：")) "user" else "assistant"
            LlmTurn(role = role, content = text.removePrefix("玩家：").removePrefix("${context.profile.name}："))
        }

        val text = runCatching { llm.complete(LlmRequest(system, turns)) }.getOrNull()
        if (text == null || text.isBlank()) return fallback.respond(context)

        return GeneratedDialogue(
            text = text.trim(),
            source = NpcMessageSource.AI,
            topic = context.parsed.topics.firstOrNull(),
        )
    }

    /** 世界与关系状态块：让 LLM 知道"此刻几点、什么天气、你们熟到什么程度"。 */
    private fun stateBlock(context: NpcDialogueContext): String {
        val world: WorldState = context.worldState
        val season = world.season?.name
        val weather = world.weather.kind.name
        val place = context.presence?.let { if (it.walking) "正走向「${it.placeName}」" else "在「${it.placeName}」" }
        return buildString {
            append("现在：${world.localDate}，${weather}。")
            if (season != null) append("季节：${season}。")
            if (place != null) append("你$place。")
            append("你们的关系：").append(context.stage.label).append('。')
        }
    }
}
