package com.rainingtrace.domain.memory

import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.HexGrid
import java.util.UUID

/**
 * RT-MEM-001~004: 创建记忆节点。
 *
 * 记忆 = 位置（连续坐标）+ 时间 + 文字/心情/标签 + 可选照片。
 * 副作用：
 * - 坐标所在 cell → MEMORIZED（迷雾是表现层，用当前 grid 现算，不持久化进记忆）
 * - 写 FootprintEvent(MEMORY_CREATED)，append-only
 */
class CreateMemoryUseCase(
    private val grid: HexGrid,
    private val clock: WorldClock,
    private val memoryRepository: MemoryRepository,
    private val explorationRepository: ExplorationRepository,
    private val footprintRepository: FootprintRepository,
) {
    suspend operator fun invoke(draft: MemoryDraft): MemoryNode {
        val now = clock.now().toEpochMilli()
        val memory = MemoryNode(
            id = UUID.randomUUID().toString(),
            createdAtEpochMs = now,
            coordinate = draft.coordinate,
            text = draft.text.trim(),
            mood = draft.mood,
            tags = draft.tags,
            mediaRefs = draft.media?.let { listOf(it.localUri) } ?: emptyList(),
        )
        memoryRepository.save(memory)

        // 坐标所在格升级为 MEMORIZED（不降级已有 SPECIAL）
        val cell = grid.cellOf(draft.coordinate)
        val state = explorationRepository.loadState()
        val updated = state.withState(cell, CellFogState.MEMORIZED)
        explorationRepository.saveStates(updated.cellStates)

        footprintRepository.append(
            FootprintEvent(
                id = UUID.randomUUID().toString(),
                timestampEpochMs = now,
                coordinate = draft.coordinate,
                eventType = FootprintEventType.MEMORY_CREATED,
                payload = mapOf("memoryId" to memory.id),
            ),
        )
        return memory
    }
}

/** 记忆草稿：编辑器里未保存的内容。 */
data class MemoryDraft(
    val coordinate: com.rainingtrace.domain.map.WorldCoordinate,
    val text: String = "",
    val mood: Mood? = null,
    val tags: Set<String> = emptySet(),
    val media: CapturedMedia? = null,
) {
    val isValid: Boolean
        get() = text.isNotBlank() || media != null
}
