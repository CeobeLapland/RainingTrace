package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.distanceMetersTo
import kotlin.math.ceil

/** 他答应得了吗。 */
sealed interface CommitmentAnswer {
    /** 答应：他走过去要 [travelMinutes] 分钟，所以会提前出发。 */
    data class Agree(val travelMinutes: Int) : CommitmentAnswer

    /** 他本来就（会）在那儿，最省事的一种。 */
    object AlreadyThere : CommitmentAnswer

    /** 那个时间他走不开（马上要去别的地方）。 */
    object Busy : CommitmentAnswer

    /** 没有可用作息，说不准（内容写坏了，不给承诺）。 */
    object Unknown : CommitmentAnswer
}

/**
 * 他答应得了吗——**由真实作息决定，不是随机数**。
 *
 * 这就是"不骗人"的另一半：片 1 靠"不许说承诺词"保证不撒谎；
 * 片 3 允许承诺，但**先算他到底去不去得了**——赶不上就说走不开。
 * 所以他说"明天下午我有事"是真的有事（作息里写着），
 * 说"说好了，明天下午我在图书馆"也是真的（作息被覆盖了）。
 */
fun answerCommitment(
    profile: NpcProfile,
    places: Map<String, Place>,
    targetMinuteOfDay: Int,
    placeId: String,
): CommitmentAnswer {
    val target = places[placeId] ?: return CommitmentAnswer.Unknown
    val schedule = resolveSchedule(profile, places)
    val here = schedule.presenceAt(targetMinuteOfDay) ?: return CommitmentAnswer.Unknown
    if (here.placeId == placeId) return CommitmentAnswer.AlreadyThere

    // 步行速度按 80 m/min（约 4.8 km/h）估；校园尺度够用，也不涉及寻路。
    val travelMinutes = ceil(
        here.coordinate.distanceMetersTo(target.coordinate) / WALK_SPEED_METERS_PER_MINUTE,
    ).toInt().coerceAtLeast(1)

    // 他后面还有安排吗？来不及走过去就走不开。
    val gap = schedule.minutesUntilNextEntry(targetMinuteOfDay) ?: return CommitmentAnswer.Unknown
    return if (gap >= travelMinutes) CommitmentAnswer.Agree(travelMinutes) else CommitmentAnswer.Busy
}

/** 约定到达后他会在原地等的时长；过了就算没来。 */
const val COMMITMENT_WINDOW_MINUTES = 60

/** 步行速度估计：80 米/分钟。 */
const val WALK_SPEED_METERS_PER_MINUTE = 80.0