package com.rainingtrace.domain.memory

import com.rainingtrace.domain.map.HexCellId

/**
 * RT-DOM-007: 记忆节点。
 *
 * 位置 + 时间 + 图片/文字 + 心情 + 标签（GDD §06）。
 * 与 FootprintEvent 一样是玩家世界史的一部分，append-only。
 */
enum class Mood {
    CALM,
    HAPPY,
    CURIOUS,
    LONELY,
    EXCITED,
    MELANCHOLY,
}

data class MemoryNode(
    val id: String,
    val createdAtEpochMs: Long,
    val cellId: HexCellId,
    val text: String = "",
    val mood: Mood? = null,
    val tags: Set<String> = emptySet(),
    /** 本地图片 URI（content://）；P1 起换 Storage 引用。 */
    val mediaRefs: List<String> = emptyList(),
    val sourceEventId: String? = null,
)

interface MemoryRepository {
    suspend fun save(memory: MemoryNode)

    suspend fun byCell(cellId: HexCellId): List<MemoryNode>

    suspend fun latest(limit: Int): List<MemoryNode>
}
