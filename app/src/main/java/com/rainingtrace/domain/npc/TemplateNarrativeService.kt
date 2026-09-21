package com.rainingtrace.domain.npc

import com.rainingtrace.domain.world.RandomSource

/**
 * 模板叙事：把回复拆成"部件"拼出来 —— 记忆钩子或招呼 + 主体 + 追问 + 风味尾。
 *
 * 三条设计约束：
 * 1. **每部件 ≥4 条变体**，用注入的 [RandomSource] 选（固定 seed → 结果可测）；
 * 2. **台词表分两层**：`"$npcId.$key"` 覆盖在前，共享 `key` 兜底；
 * 3. **避开最近 3 条用过的句子**——同一句出现三次就立刻露馅（这是最容易被忽略的一步）。
 *
 * 玩家问"某个时刻你在哪"时，主体只允许引用 [ScheduleFacts] 里的真实字段，
 * 所以他说的话与地图上明天的位置是一致的。
 */
class TemplateNarrativeService(
    private val random: RandomSource,
) : NarrativeService {

    override suspend fun respond(context: NpcDialogueContext): GeneratedDialogue {
        val npcId = context.profile.id
        val recent = context.recentMessages.filter { it.fromNpc }.map { it.text }.takeLast(LOOKBACK)

        val dialogue = when {
            // 想见面：答应了就承诺（作息已被覆盖，所以这句话是真的）；
            // 没答应（他有安排）就软拒绝 + 说明那时他在哪。
            context.parsed.wantsToMeet -> {
                val lines = mutableListOf(pick(greetKey(context.stage), npcId, recent))
                val commitment = context.commitmentFacts
                when {
                    commitment?.agreed == true -> lines += pick(
                        "commitment",
                        npcId,
                        recent,
                        mapOf("time" to commitment.timeLabel, "place" to commitment.placeName),
                    )

                    commitment != null -> lines += pick(
                        "noMeet.busy",
                        npcId,
                        recent,
                        mapOf("time" to commitment.timeLabel),
                    )

                    else -> lines += pick(noMeetKey(context.profile), npcId, recent)
                }
                // 没答应时把"那时他真在哪"补上，让拒绝也有信息量。
                if (commitment?.agreed != true) {
                    context.scheduleFacts?.let {
                        lines += pick(scheduleKey(it), npcId, recent, scheduleSlots(it))
                    }
                }
                lines += tail(context, npcId, recent)
                lines
            }

            // 问作息：如实回答
            context.parsed.asksAboutSchedule -> {
                val lines = mutableListOf(pick(greetKey(context.stage), npcId, recent))
                val facts = context.scheduleFacts
                lines += if (facts != null) {
                    pick(scheduleKey(facts), npcId, recent, scheduleSlots(facts))
                } else {
                    pick("unknownSchedule", npcId, recent)
                }
                lines += tail(context, npcId, recent)
                lines
            }

            // 聊到话题
            context.parsed.topics.isNotEmpty() -> {
                val topic = context.parsed.topics.first()
                val lines = mutableListOf(pick(greetKey(context.stage), npcId, recent))
                lines += pick("topic.$topic", npcId, recent)
                lines += tail(context, npcId, recent)
                lines
            }

            // 提到地点
            context.parsed.mentionedPlaceId != null -> {
                val name = context.parsed.mentionedPlaceName ?: ""
                val lines = mutableListOf(pick(greetKey(context.stage), npcId, recent))
                lines += pick("place", npcId, recent, mapOf("place" to name))
                lines += tail(context, npcId, recent)
                lines
            }

            // 没听懂：不装懂，只反问
            else -> listOf(pick("unparsed", npcId, recent))
        }

        // "他记得你"：有记忆素材时，用它替掉招呼（更像一个记得你的人）
        val hook = memoryHook(context, recent)
        val withHook = if (hook != null) listOf(hook) + dialogue.drop(1) else dialogue

        // 话多的人才会追问
        val withAsk = if (NpcTrait.TALKATIVE in context.profile.traits && context.parsed.isQuestion) {
            withHook + pick("ask", npcId, recent)
        } else {
            withHook
        }

        return GeneratedDialogue(
            text = withAsk.joinToString(""),
            source = NpcMessageSource.TEMPLATE,
            topic = context.parsed.topics.firstOrNull(),
        )
    }

    private fun greetKey(stage: RelationshipStage): String = "greet.${stage.name.lowercase()}"

    private fun scheduleKey(facts: ScheduleFacts): String =
        if (facts.walking) "schedule.walking" else "schedule.stay"

    private fun scheduleSlots(facts: ScheduleFacts): Map<String, String> = mapOf(
        "time" to facts.timeLabel,
        "place" to facts.placeName,
        "activity" to facts.activity,
    )

    /** 软拒绝的说法按性格分：务实 / 拘谨 / 热络 / 默认。 */
    private fun noMeetKey(profile: NpcProfile): String = when {
        NpcTrait.PRACTICAL in profile.traits -> "noMeet.practical"
        NpcTrait.RESERVED in profile.traits -> "noMeet.reserved"
        NpcTrait.WARM in profile.traits -> "noMeet.warm"
        else -> "noMeet.default"
    }

    private fun tail(context: NpcDialogueContext, npcId: String, recent: Collection<String>): String {
        val key = "tail.${context.mood.name}"
        return if (key in NpcLineCatalog.LINES) pick(key, npcId, recent) else ""
    }

    /**
     * "他记得你"：有素材就用它替掉招呼。
     *
     * 这里**不再按关系阶段设门槛**——素材本身已经是"该不该说"的判据
     * （见 `SendNpcMessageUseCase.memoryHooksFor`：要么隔了一天以上，要么是
     * 遇见过但还没说过话）。刚认识的人提起"那天在湖边跟我打招呼的"，
     * 正是想要的那种"他记得你"，而不是失礼。
     */
    private fun memoryHook(context: NpcDialogueContext, recent: Collection<String>): String? {
        val hooks = context.memoryHooks.filterNot { hook -> recent.any { it.contains(hook) } }
        if (hooks.isEmpty()) return null
        return hooks[random.nextInt(hooks.size)]
    }

    /**
     * 取一条台词：NPC 专属覆盖 → 共享；再避开最近用过的；
     * 全都用过就退回全集（宁可重复也不留空）。
     *
     * 去重用**包含**判断：最近的消息是"招呼+主体"拼起来的整句，
     * 单条台词只是它的一部分，等值比较永远匹配不上。
     */
    private fun pick(
        key: String,
        npcId: String,
        recent: Collection<String>,
        slots: Map<String, String> = emptyMap(),
    ): String {
        val variants = NpcLineCatalog.LINES["$npcId.$key"]
            ?: NpcLineCatalog.LINES[key]
            ?: NpcLineCatalog.LINES.getValue("unparsed")
        val filled = variants.map { fill(it, slots) }
        val fresh = filled.filterNot { variant -> recent.any { it.contains(variant) } }
        val pool = fresh.ifEmpty { filled }
        return pool[random.nextInt(pool.size)]
    }

    private fun fill(template: String, slots: Map<String, String>): String {
        var result = template
        slots.forEach { (key, value) -> result = result.replace("{$key}", value) }
        return result
    }

    private companion object {
        /** 去重回看几条：3 条足够避开"连着说同一句"。 */
        const val LOOKBACK = 3
    }
}
