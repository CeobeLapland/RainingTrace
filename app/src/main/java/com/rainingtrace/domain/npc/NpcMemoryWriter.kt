package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.core.time.WorldClock
import java.time.LocalDate

/**
 * 记忆的选择与维护——**全是纯函数**，方便单测（数据层没有单测，见 HANDOFF_7）。
 */
object NpcMemoryMaintenance {

    /** 每个 NPC 记忆条数上限；超过就把最低优先级的合并成 SUMMARY。 */
    const val PRUNE_KEEP = 40

    /** 每次清理最多合并几条低分事实进一条 SUMMARY（避免 SUMMARY 自己爆炸）。 */
    const val SUMMARY_BATCH = 5

    /**
     * 挑进 prompt 的事实条数上限。
     * 上下文预算的第三道闸（前两道是 [PromptBudget] 的字符上限 + 最近消息回看）。
     */
    const val PROMPT_TOP_K = 12

    /**
     * 挑选：重要性降序，同级按"最近被想起"降序、再按创建时间新的在前。
     * 返回前 [k] 条。
     */
    fun topK(facts: List<NpcMemoryFact>, k: Int = PROMPT_TOP_K): List<NpcMemoryFact> =
        facts.sortedWith(
            compareByDescending<NpcMemoryFact> { it.importance }
                .thenByDescending { it.lastReferencedAtEpochMs }
                .thenByDescending { it.createdAtEpochMs },
        ).take(k)

    /**
     * 清理：超过 [PRUNE_KEEP] 时，把得分最低的一批（最多 [SUMMARY_BATCH] 条）
     * 用 [summarize] 并成一条 SUMMARY；已有 SUMMARY 则并入最新那条。
     *
     * @return (保留的, 被合并删除的, 新生成的 SUMMARY（可能为 null）)。
     */
    fun prune(
        facts: List<NpcMemoryFact>,
        nowEpochMs: Long,
    ): Triple<List<NpcMemoryFact>, List<NpcMemoryFact>, NpcMemoryFact?> {
        if (facts.size <= PRUNE_KEEP) return Triple(facts, emptyList(), null)

        val keepCount = facts.size - PRUNE_KEEP + 1 // 留一个位给新 SUMMARY
        val dropped = facts.sortedBy { it.importance }
            .take(keepCount.coerceAtMost(SUMMARY_BATCH))

        // 已有 SUMMARY 就并入最新那条，避免 SUMMARY 自己越积越多
        val latestSummary = facts
            .filter { it.kind == NpcMemoryKind.SUMMARY }
            .maxByOrNull { it.createdAtEpochMs }

        val summary = if (latestSummary != null) {
            latestSummary.copy(
                content = (latestSummary.content + "；" + summarize(dropped)).take(SUMMARY_MAX_CHARS),
            )
        } else {
            NpcMemoryFact(
                id = "mem.summary.${dropped.first().npcId}.$nowEpochMs",
                npcId = dropped.first().npcId,
                kind = NpcMemoryKind.SUMMARY,
                content = summarize(dropped),
                importance = SUMMARY_IMPORTANCE,
                source = "summary",
                createdAtEpochMs = nowEpochMs,
            )
        }

        val remaining = facts - dropped.toSet()
        return Triple(remaining, dropped, summary)
    }

    /**
     * 规则版摘要：把几条低分事实并成一句"过去的事"。
     * LLM 版留插槽（离线把更多对话压成一段），接口不变。
     */
    fun summarize(facts: List<NpcMemoryFact>): String =
        facts.joinToString("；") { it.content }.take(SUMMARY_MAX_CHARS)

    /**
     * 新增事实按内容去重（同 NPC 同内容不重复记）。
     * 返回"还没记过"的那些。
     */
    fun dedupe(existing: List<NpcMemoryFact>, candidates: List<NpcMemoryFact>): List<NpcMemoryFact> {
        val known = existing.map { it.content }.toSet()
        return candidates.filter { it.content !in known }
    }

    /** 一次对话里能产出的候选事实（不判重，交给 [dedupe]）。 */
    fun factsFromDialogue(
        npcId: String,
        npcName: String,
        parsed: ParsedPlayerMessage,
        mentionedPlaceName: String?,
        commitmentFacts: CommitmentFacts?,
        nowEpochMs: Long,
    ): List<NpcMemoryFact> {
        val facts = mutableListOf<NpcMemoryFact>()

        parsed.topics.take(2).forEach { topic ->
            facts += NpcMemoryFact(
                id = "mem.pref.$npcId.${topic.name}.$nowEpochMs",
                npcId = npcId,
                kind = NpcMemoryKind.PREFERENCE,
                content = "玩家最近爱聊「${topic.label}」。",
                importance = PREFERENCE_IMPORTANCE,
                source = "chat",
                createdAtEpochMs = nowEpochMs,
            )
        }

        if (mentionedPlaceName != null) {
            facts += NpcMemoryFact(
                id = "mem.place.$npcId.$nowEpochMs",
                npcId = npcId,
                kind = NpcMemoryKind.PLACE,
                content = "玩家跟「$npcName」提过「$mentionedPlaceName」。",
                importance = PLACE_IMPORTANCE,
                source = "chat",
                createdAtEpochMs = nowEpochMs,
            )
        }

        if (commitmentFacts?.agreed == true) {
            facts += NpcMemoryFact(
                id = "mem.commit.$npcId.$nowEpochMs",
                npcId = npcId,
                kind = NpcMemoryKind.COMMITMENT,
                content = "约定好了：${commitmentFacts.timeLabel}在「${commitmentFacts.placeName}」见面。",
                importance = COMMITMENT_IMPORTANCE,
                source = "chat",
                createdAtEpochMs = nowEpochMs,
            )
        }

        return facts
    }

    /** 玩家在某天第一次遇见这个 NPC → 记一条"第一次遇见"。 */
    fun factFromFirstMeeting(
        npcId: String,
        placeName: String,
        nowEpochMs: Long,
    ): NpcMemoryFact = NpcMemoryFact(
        id = "mem.met.$npcId.$nowEpochMs",
        npcId = npcId,
        kind = NpcMemoryKind.MET,
        content = "第一次在「$placeName」遇见玩家。",
        importance = MET_IMPORTANCE,
        source = "footprint",
        createdAtEpochMs = nowEpochMs,
    )

    private const val PREFERENCE_IMPORTANCE = 2
    private const val PLACE_IMPORTANCE = 3
    private const val COMMITMENT_IMPORTANCE = 7
    private const val MET_IMPORTANCE = 5
    private const val SUMMARY_IMPORTANCE = 5
    private const val SUMMARY_MAX_CHARS = 120
}

/**
 * 把一次对话/一次遇见"沉淀成记忆"的编排。
 *
 * 在 [com.rainingtrace.domain.npc.SendNpcMessageUseCase] 与遇见记录之后调用：
 * 提取候选 → 对现存判重 → 落库 → 超限清理。全在同一个 suspend 里，保证
 * "聊完这次，他就真的记得了"。
 */
class NpcMemoryWriter(
    private val repository: NpcMemoryRepository,
    private val clock: WorldClock,
) {
    suspend fun recordFromDialogue(
        npcId: String,
        npcName: String,
        parsed: ParsedPlayerMessage,
        mentionedPlaceName: String?,
        commitmentFacts: CommitmentFacts?,
    ) {
        val now = clock.now().toEpochMilli()
        val existing = repository.factsFor(npcId)
        val candidates = NpcMemoryMaintenance.factsFromDialogue(
            npcId = npcId,
            npcName = npcName,
            parsed = parsed,
            mentionedPlaceName = mentionedPlaceName,
            commitmentFacts = commitmentFacts,
            nowEpochMs = now,
        )
        val fresh = NpcMemoryMaintenance.dedupe(existing, candidates)
        fresh.forEach { repository.append(it) }
        maintain(npcId, now)
    }

    /** 玩家第一次遇见 → 记一条；同 NPC 已存在 MET 就不重复（去重由 content 判）。 */
    suspend fun recordFirstMeeting(npcId: String, placeName: String) {
        val now = clock.now().toEpochMilli()
        val existing = repository.factsFor(npcId)
        val candidate = NpcMemoryMaintenance.factFromFirstMeeting(npcId, placeName, now)
        if (NpcMemoryMaintenance.dedupe(existing, listOf(candidate)).isNotEmpty()) {
            repository.append(candidate)
            maintain(npcId, now)
        }
    }

    /** 超限清理：删除被合并的低分事实，并落库新生成的 SUMMARY（如有）。 */
    private suspend fun maintain(npcId: String, nowEpochMs: Long) {
        val current = repository.factsFor(npcId)
        val (_, dropped, summary) = NpcMemoryMaintenance.prune(current, nowEpochMs)
        if (dropped.isNotEmpty()) {
            repository.remove(dropped.map { it.id })
        }
        summary?.let { repository.append(it) }
    }

    /** 手写一条记忆（玩家/开发者编辑入口）：判重 + 超限清理。 */
    suspend fun addManual(npcId: String, content: String, importance: Int) {
        val text = content.trim()
        if (text.isEmpty()) return
        val now = clock.now().toEpochMilli()
        val existing = repository.factsFor(npcId)
        val candidate = NpcMemoryFact(
            id = "mem.manual.$npcId.${now}",
            npcId = npcId,
            kind = NpcMemoryKind.EVENT,
            content = text,
            importance = importance.coerceIn(1, 10),
            source = "player",
            createdAtEpochMs = now,
        )
        if (NpcMemoryMaintenance.dedupe(existing, listOf(candidate)).isNotEmpty()) {
            repository.append(candidate)
            maintain(npcId, now)
        }
    }

    /** 编辑一条已有记忆（内容/重要度整行覆盖）。 */
    suspend fun updateFact(fact: NpcMemoryFact) {
        repository.upsert(fact)
    }

    /** 删除一条记忆。 */
    suspend fun deleteFact(id: String) {
        repository.remove(listOf(id))
    }
}
