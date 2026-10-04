package com.rainingtrace.domain.npc

/**
 * 记忆的"编辑入口"（玩家 / 开发者共用）：加载、编辑、删除、手写添加。
 *
 * 玩家可以在档案页看到并改动 NPC 记得的事——这贴合"世界年鉴是自己写的"；
 * 开发者也能用同一入口快速给某个 NPC 造记忆，不用手改数据库。
 */
class NpcMemoryEditor(
    private val repository: NpcMemoryRepository,
    private val writer: NpcMemoryWriter,
) {
    suspend fun factsFor(npcId: String): List<NpcMemoryFact> =
        repository.factsFor(npcId).sortedByDescending { it.importance }

    /** 编辑内容与重要度（整行覆盖，id/时间/来源不动）。 */
    suspend fun update(npcId: String, id: String, content: String, importance: Int) {
        val text = content.trim()
        if (text.isEmpty()) return
        val existing = repository.factsFor(npcId).firstOrNull { it.id == id } ?: return
        writer.updateFact(
            existing.copy(
                content = text,
                importance = importance.coerceIn(1, 10),
            ),
        )
    }

    suspend fun delete(npcId: String, id: String) {
        writer.deleteFact(id)
    }

    /** 手写一条（去重 + 超限合并交给 writer）。 */
    suspend fun add(npcId: String, content: String, importance: Int) {
        writer.addManual(npcId, content, importance)
    }
}