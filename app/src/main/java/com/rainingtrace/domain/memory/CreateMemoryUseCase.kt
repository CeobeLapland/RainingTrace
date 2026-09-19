package com.rainingtrace.domain.memory

import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.GridManager
import com.rainingtrace.domain.world.WorldStateProvider
import com.rainingtrace.domain.world.footprintKeys
import java.util.UUID

/**
 * RT-MEM-001~004: 创建记忆节点。
 *
 * 记忆 = 位置（连续坐标）+ 时间 + 文字/心情/标签 + 可选照片/语音 + **当时的天气与季节**。
 * 副作用：
 * - 坐标所在 cell → MEMORIZED（迷雾是表现层，用当前 grid 现算，不持久化进记忆）
 * - 写 FootprintEvent(MEMORY_CREATED)，append-only，payload 带上世界状态
 *
 * 为什么记忆要记天气：GDD §06/§16 的"同一个地方在不同天气/季节下重复拍"、
 * 时间考古与世界档案都靠它重建；事后无法补，所以创建时就落档。
 */
class CreateMemoryUseCase(
    private val gridManager: GridManager,
    private val clock: WorldClock,
    private val memoryRepository: MemoryRepository,
    private val explorationRepository: ExplorationRepository,
    private val footprintRepository: FootprintRepository,
    private val worldState: WorldStateProvider,
) {
    suspend operator fun invoke(draft: MemoryDraft): MemoryNode {
        val now = clock.now().toEpochMilli()
        val world = worldState.current()
        val memory = MemoryNode(
            id = UUID.randomUUID().toString(),
            createdAtEpochMs = now,
            coordinate = draft.coordinate,
            text = draft.text.trim(),
            mood = draft.mood,
            tags = draft.tags,
            mediaRefs = draft.media.map { it.localUri },
            audioRef = draft.audio?.localUri,
            weather = world.weather.kind,
            season = world.season,
        )
        memoryRepository.save(memory)

        // 坐标所在格升级为 MEMORIZED（不降级已有 SPECIAL）
        val cell = gridManager.grid.cellOf(draft.coordinate)
        val state = explorationRepository.loadState()
        val updated = state.withState(cell, CellFogState.MEMORIZED)
        explorationRepository.saveStates(updated.cellStates)

        footprintRepository.append(
            FootprintEvent(
                id = UUID.randomUUID().toString(),
                timestampEpochMs = now,
                coordinate = draft.coordinate,
                eventType = FootprintEventType.MEMORY_CREATED,
                payload = buildMap {
                    put("memoryId", memory.id)
                    putAll(world.footprintKeys())
                },
            ),
        )
        return memory
    }
}

/** 记忆草稿：编辑器里未保存的内容。照片可多张，语音最多一段，三者至少有一项。 */
data class MemoryDraft(
    val coordinate: com.rainingtrace.domain.map.WorldCoordinate,
    val text: String = "",
    val mood: Mood? = null,
    val tags: Set<String> = emptySet(),
    val media: List<CapturedMedia> = emptyList(),
    val audio: CapturedAudio? = null,
) {
    val isValid: Boolean
        get() = text.isNotBlank() || media.isNotEmpty() || audio != null
}
