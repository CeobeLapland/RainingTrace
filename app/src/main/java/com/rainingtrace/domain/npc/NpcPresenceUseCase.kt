package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.settings.NpcClockOffset
import com.rainingtrace.domain.world.WorldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 算出 NPC 在给定世界状态（其实就是"几点"）下的位置与状态。
 *
 * 纯计算：读档案 + 地点坐标，按"当天第几分钟"求值，不写任何状态。
 * 地图渲染、遇见判定、消息腿都从这里取"某人此刻在哪"。
 *
 * [clockOffset] 是**开发者模式**的时间偏移，只挪 NPC 的分钟数（见 [NpcClockOffset]）；
 * 默认"真实时间"，所以玩法与既有测试不受影响。遇见判定也走这里，
 * 所以"你看到的"和"你能遇到的"永远是同一个位置。
 */
class NpcPresenceUseCase(
    private val npcRepository: NpcRepository,
    private val placeRepository: PlaceRepository,
    private val clockOffset: StateFlow<NpcClockOffset> = MutableStateFlow(NpcClockOffset.DEFAULT),
) {

    /** 全部有有效作息的 NPC 此刻的状态；内容写坏（地点不存在）的人会被跳过。 */
    suspend fun presencesAt(state: WorldState): List<NpcPresence> {
        val places = placeRepository.all().associateBy { it.id }
        val minute = minuteOfDayFor(state)
        return npcRepository.all().mapNotNull { profile ->
            resolveSchedule(profile, places).presenceAt(minute)
        }
    }

    /** 单个 NPC 此刻的状态；id 不存在或无有效作息时返回 null。 */
    suspend fun presenceOf(npcId: String, state: WorldState): NpcPresence? {
        val profile = npcRepository.byId(npcId) ?: return null
        val places = placeRepository.all().associateBy { it.id }
        return resolveSchedule(profile, places).presenceAt(minuteOfDayFor(state))
    }

    /** 真实分钟数 + 调试偏移，按天回绕。 */
    private fun minuteOfDayFor(state: WorldState): Int =
        (state.minuteOfDay + clockOffset.value.minutes).mod(MINUTES_PER_DAY)
}
