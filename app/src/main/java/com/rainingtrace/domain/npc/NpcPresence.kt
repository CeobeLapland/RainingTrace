package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.lerpTo

/**
 * NPC 此刻的状态：在哪、在做什么、是不是正在走路。
 *
 * 全部由 [ResolvedSchedule.presenceAt] 从时间算出来（纯函数），不落库。
 */
data class NpcPresence(
    val npcId: String,
    val npcName: String,
    val coordinate: WorldCoordinate,
    /** 走路时是**目的地**的地点与活动（"正在往图书馆走，去自习"）。 */
    val placeId: String,
    val placeName: String,
    val activity: String,
    val walking: Boolean,
    /** 走路进度 0..1；不走路时为 0。 */
    val progress: Double,
)

/** 已解析出坐标的作息条目（地点 id 都验证过存在）。 */
data class ResolvedEntry(
    val startMinute: Int,
    val travelMinutes: Int,
    val coordinate: WorldCoordinate,
    val placeId: String,
    val placeName: String,
    val activity: String,
)

/**
 * 把档案里的作息解析成"能算位置"的形式。
 *
 * 两个修正：
 * - **引用了不存在地点的条目直接剔除**（内容写错了不该崩，只是这个人不会出现）；
 * - **行走时长夹紧到 `间隔 - 1`**，保证走路不吃掉整段停留；相邻条目同地点则不走。
 */
fun resolveSchedule(profile: NpcProfile, places: Map<String, Place>): ResolvedSchedule {
    val resolved = profile.schedule
        .mapNotNull { entry ->
            val place = places[entry.placeId] ?: return@mapNotNull null
            ResolvedEntry(
                startMinute = entry.startMinute,
                travelMinutes = entry.travelMinutes,
                coordinate = place.coordinate,
                placeId = place.id,
                placeName = place.name,
                activity = entry.activity,
            )
        }
        .sortedBy { it.startMinute }

    if (resolved.isEmpty()) return ResolvedSchedule(profile.id, profile.name, emptyList())

    val n = resolved.size
    val clamped = resolved.mapIndexed { index, entry ->
        val previous = resolved[(index - 1 + n) % n]
        val gap = (entry.startMinute - previous.startMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val travel = when {
            n == 1 -> 0
            entry.placeId == previous.placeId -> 0
            else -> entry.travelMinutes.coerceIn(0, (gap - 1).coerceAtLeast(0))
        }
        entry.copy(travelMinutes = travel)
    }
    return ResolvedSchedule(profile.id, profile.name, clamped)
}

/**
 * 解析后的作息。位置是**时间的纯函数**——同一个时刻永远算出同一个位置，
 * 所以不需要 tick 驱动状态，也不需要落库。
 */
class ResolvedSchedule(
    private val npcId: String,
    private val npcName: String,
    private val entries: List<ResolvedEntry>,
) {

    /**
     * 当天第 [minuteOfDay] 分钟时这个人在哪。
     *
     * 作息按**天环**处理：找"最后一条已到达的条目"作为当前位置，
     * 再看下一条的行走窗口是否正在发生（窗口跨零点也成立）。
     * 无有效作息时返回 null（这个人不出现）。
     */
    fun presenceAt(minuteOfDay: Int): NpcPresence? {
        if (entries.isEmpty()) return null

        val n = entries.size
        val hereIndex = entries.indexOfLast { it.startMinute <= minuteOfDay }
            .let { if (it >= 0) it else n - 1 }
        val nextIndex = (hereIndex + 1) % n
        val target = entries[nextIndex]

        // 距到达目的地还有几分钟（0 = 刚到）
        val untilArrival = (target.startMinute - minuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val walking = target.travelMinutes > 0 && untilArrival in 1..target.travelMinutes

        if (!walking) {
            val here = entries[hereIndex]
            return NpcPresence(
                npcId = npcId,
                npcName = npcName,
                coordinate = here.coordinate,
                placeId = here.placeId,
                placeName = here.placeName,
                activity = here.activity,
                walking = false,
                progress = 0.0,
            )
        }

        val progress = 1.0 - untilArrival.toDouble() / target.travelMinutes
        return NpcPresence(
            npcId = npcId,
            npcName = npcName,
            coordinate = entries[hereIndex].coordinate.lerpTo(target.coordinate, progress),
            placeId = target.placeId,
            placeName = target.placeName,
            activity = target.activity,
            walking = true,
            progress = progress,
        )
    }
}
