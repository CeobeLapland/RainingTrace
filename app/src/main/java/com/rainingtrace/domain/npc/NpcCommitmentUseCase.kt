package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.world.WorldStateProvider
import java.util.UUID

/**
 * 约定到期时的兑现检查：他到底去了没有、玩家有没有来。
 *
 * **他一定会在那儿**这件事已经由作息覆盖保证了（见 [NpcPresenceUseCase]），
 * 这里只做两件事：他到了就发一句、玩家没来就记一次"没等到"。
 *
 * 与片 2 的主动消息引擎共用同一套东西：同一个 tick 循环、同一个 [NpcMessageWriter]、
 * 同样的足迹留档方式。区别只在"状态就是承诺本身"（AGREED → KEPT/MISSED），
 * 所以这里不需要额外的去重记录。
 */
class NpcCommitmentUseCase(
    private val clock: WorldClock,
    private val commitments: NpcCommitmentRepository,
    private val placeRepository: PlaceRepository,
    private val messageWriter: NpcMessageWriter,
    private val stateRepository: NpcStateRepository,
    /** MISSED 只写足迹（不发消息）——"他没等到你"由主动消息规则读这条足迹来提。 */
    private val footprintRepository: FootprintRepository,
    private val worldState: WorldStateProvider,
) {

    /** 返回这次发出的消息（"我到了"）；没有就是 null。 */
    suspend fun tick(playerCoordinate: WorldCoordinate?): NpcMessage? {
        val now = clock.now().toEpochMilli()
        val world = worldState.current()
        val todayKey = world.localDate.toString()
        val places = placeRepository.all().associateBy { it.id }

        for (commitment in commitments.all().filter { it.isOpen }) {
            val due = commitment.dateKey < todayKey ||
                (commitment.dateKey == todayKey && world.minuteOfDay >= commitment.startMinute)
            if (!due) continue

            val place = places[commitment.placeId]
            if (place == null) {
                // 地点配置被删了：别让它永远挂着。
                resolve(commitment, NpcCommitmentStatus.MISSED, place?.coordinate, now)
                continue
            }

            val pastEnd = commitment.dateKey < todayKey ||
                world.minuteOfDay >= commitment.endMinute
            val playerCame = playerCoordinate != null &&
                playerCoordinate.distanceMetersTo(place.coordinate) <= KEPT_RANGE_METERS

            when {
                // 还在等：玩家一来就算兑现（不能等到窗口结束才判，那会漏掉"来过又走了"）。
                !pastEnd && playerCame -> {
                    resolve(commitment, NpcCommitmentStatus.KEPT, place.coordinate, now)
                    rewardMeeting(commitment.npcId, todayKey, now)
                    return messageWriter.write(
                        npcId = commitment.npcId,
                        coordinate = place.coordinate,
                        text = "我到了，在「${place.name}」。",
                        eventType = FootprintEventType.NPC_COMMITMENT_KEPT,
                        payload = mapOf("commitmentId" to commitment.id),
                        nowEpochMs = now,
                    )
                }

                // 窗口过了才回到前台/打开应用：他确实去了，只是没能当场说一声。
                pastEnd && playerCame -> {
                    resolve(commitment, NpcCommitmentStatus.KEPT, place.coordinate, now)
                    rewardMeeting(commitment.npcId, todayKey, now)
                }

                // 窗口过了还没来：记一次"没等到"，他下次聊天会提一句。
                pastEnd && !playerCame -> {
                    resolve(commitment, NpcCommitmentStatus.MISSED, place.coordinate, now)
                }

                else -> Unit
            }
        }
        return null
    }

    /**
     * 结掉一条约定：改状态，并按结果留一条足迹。
     *
     * MISSED 的足迹是"他没等到你"的唯一来源——主动消息规则读它才会提一句，
     * 所以这一步不能省（漏了的话"你没去"就完全没有后果）。
     */
    private suspend fun resolve(
        commitment: NpcCommitment,
        status: NpcCommitmentStatus,
        coordinate: WorldCoordinate?,
        nowEpochMs: Long,
    ) {
        commitments.save(commitment.copy(status = status, resolvedAtEpochMs = nowEpochMs))
        if (status != NpcCommitmentStatus.MISSED || coordinate == null) return
        footprintRepository.append(
            FootprintEvent(
                id = UUID.randomUUID().toString(),
                timestampEpochMs = nowEpochMs,
                coordinate = coordinate,
                eventType = FootprintEventType.NPC_COMMITMENT_MISSED,
                payload = mapOf("npcId" to commitment.npcId, "commitmentId" to commitment.id),
            ),
        )
    }

    /** 兑现给一点好感——他等了，你也来了，这是双向的事。 */
    private suspend fun rewardMeeting(npcId: String, todayKey: String, nowEpochMs: Long) {
        val current = applyDailyReset(stateRepository.stateOf(npcId), todayKey)
        val (affection, gain) = affectionAfter(
            current = current.affection,
            delta = MEETING_AFFECTION,
            todayGain = current.todayAffectionGain,
        )
        stateRepository.save(
            current.copy(
                affection = affection,
                todayAffectionGain = gain,
                lastInteractionAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    private companion object {
        /** 走到跟前才算"来了"：与遇见 NPC 同一尺度。 */
        const val KEPT_RANGE_METERS = 40.0
        const val MEETING_AFFECTION = 2
    }
}