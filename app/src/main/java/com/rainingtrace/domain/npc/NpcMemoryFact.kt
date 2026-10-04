package com.rainingtrace.domain.npc

/**
 * NPC 对玩家/世界的一段记忆（N4 记忆层）。
 *
 * 与足迹（footprint_events，事件流，append-only）不同：这里是**可被挑选进 LLM
 * 上下文**的高价值事实。设计约束：
 *
 * - **有界**：每个 NPC 的记忆条数封顶（见 [NpcMemoryMaintenance.PRUNE_KEEP]），
 *   超了把最旧最低优先级的合并成一条 SUMMARY——绝不放任无限增长。
 * - **可去重**：内容完全相同的就不重复记（[NpcMemoryWriter] 按 content 判重）。
 * - **重要性驱动**：prompt 组装时按 `importance` 降序挑，预算不够就丢最低的。
 */
data class NpcMemoryFact(
    val id: String,
    val npcId: String,
    val kind: NpcMemoryKind,
    /** 一句话事实，直接进 LLM 上下文（以"你/他"第二/三人称写好）。 */
    val content: String,
    /** 1..10：越高越该被记住、越先进 prompt。 */
    val importance: Int,
    /** 来源标签："chat" / "footprint" / "proactive" / "summary"。 */
    val source: String,
    val createdAtEpochMs: Long,
    /** 最近一次被用进 prompt 的时间；挑记忆时"近期被想起"也算新鲜。 */
    val lastReferencedAtEpochMs: Long = 0L,
    /** 被引用次数；低分事实之间用它破平。 */
    val refCount: Int = 0,
)

enum class NpcMemoryKind(val label: String) {
    MET("见过面"),
    TALKED("聊过天"),
    PLACE("去过的地方"),
    GIFT("收过礼物"),
    COMMITMENT("约定"),
    PREFERENCE("喜好"),
    EVENT("发生过的事"),
    SUMMARY("长期总结"),
}
