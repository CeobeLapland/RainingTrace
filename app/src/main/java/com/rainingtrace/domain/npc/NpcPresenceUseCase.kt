package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.world.WorldState

/**
 * 算出 NPC 在给定世界状态（其实就是"几点"）下的位置与状态。
 *
 * 纯计算：读档案 + 地点坐标，按 [WorldState.minuteOfDay] 求值，不写任何状态。
 * 地图渲染、遇见判定、将来的消息腿都从这里取"某人此刻在哪"。
 */
class NpcPresenceUseCase(
    private val npcRepository: NpcRepository,
    private val placeRepository: PlaceRepository,
) {

    /** 全部有有效作息的 NPC 此刻的状态；内容写坏（地点不存在）的人会被跳过。 */
    suspend fun presencesAt(state: WorldState): List<NpcPresence> {
        val places = placeRepository.all().associateBy { it.id }
        return npcRepository.all().mapNotNull { profile ->
            resolveSchedule(profile, places).presenceAt(state.minuteOfDay)
        }
    }

    /** 单个 NPC 此刻的状态；id 不存在或无有效作息时返回 null。 */
    suspend fun presenceOf(npcId: String, state: WorldState): NpcPresence? {
        val profile = npcRepository.byId(npcId) ?: return null
        val places = placeRepository.all().associateBy { it.id }
        return resolveSchedule(profile, places).presenceAt(state.minuteOfDay)
    }
}
