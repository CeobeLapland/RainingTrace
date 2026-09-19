package com.rainingtrace.domain.memory

import com.rainingtrace.domain.map.WorldCoordinate

/**
 * RT-DOM-007: 记忆节点。
 *
 * 位置 + 时间 + 图片/文字 + 心情 + 标签（GDD §06）。
 * 位置是连续坐标，不依附六边形格子；与 FootprintEvent 一样 append-only。
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
    val coordinate: WorldCoordinate,
    val text: String = "",
    val mood: Mood? = null,
    val tags: Set<String> = emptySet(),
    /** 本地图片 URI（content://）；P1 起换 Storage 引用。可多张。 */
    val mediaRefs: List<String> = emptyList(),
    /** 本地语音 URI；MVP 每条记忆最多一段。 */
    val audioRef: String? = null,
    val sourceEventId: String? = null,
)

interface MemoryRepository {
    suspend fun save(memory: MemoryNode)

    suspend fun latest(limit: Int): List<MemoryNode>
}
