package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.settings.NpcClockOffset
import com.rainingtrace.domain.world.WorldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 算出 NPC 在给定世界状态（其实就是"几点 + 哪天"）下的位置与状态。
 *
 * 纯计算：读档案 + 地点坐标 + **当天生效的承诺**，按"当天第几分钟"求值，不写任何状态。
 * 地图渲染、遇见判定、消息腿都从这里取"某人此刻在哪"。
 *
 * [commitments] 是承诺仓储（片 3）：约定那天他会被"覆盖"到约定的地点，
 * 所以他说过的话和地图上的位置自动一致——不需要另写一套逻辑去挪他。
 * [clockOffset] 是**开发者模式**的时间偏移，只挪 NPC 的分钟数（见 [NpcClockOffset]）；
 * 默认"真实时间"，所以玩法与既有测试不受影响。
 */
class NpcPresenceUseCase(
    private val npcRepository: NpcRepository,
    private val placeRepository: PlaceRepository,
    private val clockOffset: StateFlow<NpcClockOffset> = MutableStateFlow(NpcClockOffset.DEFAULT),
    private val commitments: NpcCommitmentRepository? = null,
) {

    /** 全部有有效作息的 NPC 此刻的状态；内容写坏（地点不存在）的人会被跳过。 */
    suspend fun presencesAt(state: WorldState): List<NpcPresence> {
        val places = placeRepository.all().associateBy { it.id }
        val overrides = openOverrides(state)
        val minute = minuteOfDayFor(state)
        return npcRepository.all().mapNotNull { profile ->
            resolveSchedule(profile, places, overrides[profile.id].orEmpty()).presenceAt(minute)
        }
    }

    /** 单个 NPC 此刻的状态；id 不存在或无有效作息时返回 null。 */
    suspend fun presenceOf(npcId: String, state: WorldState): NpcPresence? {
        val profile = npcRepository.byId(npcId) ?: return null
        val places = placeRepository.all().associateBy { it.id }
        return resolveSchedule(profile, places, openOverrides(state)[npcId].orEmpty())
            .presenceAt(minuteOfDayFor(state))
    }

    /**
     * 当天生效的承诺 → 按 npcId 分组的作息覆盖。
     *
     * 只取"就在今天且还没结束"的那几条：约定是**当天**的，明天的现在不该生效。
     */
    private suspend fun openOverrides(state: WorldState): Map<String, List<NpcScheduleOverride>> {
        val repo = commitments ?: return emptyMap()
        val todayKey = state.localDate.toString()
        return repo.all()
            .filter { it.isOpen && it.dateKey == todayKey && state.minuteOfDay >= it.startMinute }
            .groupBy { it.npcId }
            .mapValues { (_, list) ->
                list.map {
                    NpcScheduleOverride(
                        placeId = it.placeId,
                        startMinute = it.startMinute,
                        endMinute = it.endMinute,
                        activity = COMMITMENT_ACTIVITY,
                        // 带行走时长，所以他"往约定地点走"那段在地图上也是真的。
                        travelMinutes = it.travelMinutes,
                    )
                }
            }
    }

    /** 真实分钟数 + 调试偏移，按天回绕。 */
    private fun minuteOfDayFor(state: WorldState): Int =
        (state.minuteOfDay + clockOffset.value.minutes).mod(MINUTES_PER_DAY)

    private companion object {
        /** 约定窗口里他做的事：这句话会出现在地图卡片和消息页。 */
        const val COMMITMENT_ACTIVITY = "在这儿等你"
    }
}