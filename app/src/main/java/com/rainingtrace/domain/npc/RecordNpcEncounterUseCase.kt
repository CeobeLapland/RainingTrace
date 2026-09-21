package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.world.WorldStateProvider
import com.rainingtrace.domain.world.footprintKeys
import java.util.UUID

/**
 * 玩家走到 NPC 跟前时，把"第一次遇见"记进足迹。
 *
 * 只记**第一次**：之后每次靠近都写一条会把足迹淹掉，而且"见过谁"是 append-only
 * 历史里能直接查出来的事实（同 [FootprintEventType.NPC_MET] 的 payload），
 * 所以不需要额外的持久化状态。
 */
class RecordNpcEncounterUseCase(
    private val clock: WorldClock,
    private val footprintRepository: FootprintRepository,
    private val worldState: WorldStateProvider,
) {

    suspend operator fun invoke(
        playerCoordinate: WorldCoordinate,
        presence: NpcPresence,
    ): NpcEncounterResult {
        val distance = playerCoordinate.distanceMetersTo(presence.coordinate)
        if (distance > MEET_RANGE_METERS) return NpcEncounterResult.TooFar

        val metBefore = footprintRepository.eventsOfType(FootprintEventType.NPC_MET)
            .any { it.payload["npcId"] == presence.npcId }
        if (metBefore) return NpcEncounterResult.AlreadyMet(presence.npcId, presence.npcName)

        footprintRepository.append(
            FootprintEvent(
                id = UUID.randomUUID().toString(),
                timestampEpochMs = clock.now().toEpochMilli(),
                coordinate = playerCoordinate,
                eventType = FootprintEventType.NPC_MET,
                payload = buildMap {
                    put("npcId", presence.npcId)
                    put("placeId", presence.placeId)
                    put("activity", presence.activity)
                    put("walking", presence.walking.toString())
                    put("distanceMeters", distance.toString())
                    // 和地点动作一样，把当时的世界状态一起留档。
                    putAll(worldState.current().footprintKeys())
                },
            ),
        )
        return NpcEncounterResult.Met(presence.npcId, presence.npcName)
    }

    companion object {
        /**
         * 遇见是"走到跟前"打招呼：观察可以站 120m、采集要 60m，
         * 但认出一个人的距离得再近一截。
         */
        const val MEET_RANGE_METERS = 30.0
    }
}

sealed interface NpcEncounterResult {
    data class Met(val npcId: String, val npcName: String) : NpcEncounterResult
    data class AlreadyMet(val npcId: String, val npcName: String) : NpcEncounterResult
    object TooFar : NpcEncounterResult
}
