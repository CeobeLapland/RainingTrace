package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.Place
import java.time.LocalDate

/**
 * 玩家消息里的时间提示（粗粒度，够用即可——不做日期解析）。
 *
 * 之所以能"如实回答明天下午在哪"而不用跨天特判：[ResolvedSchedule.presenceAt]
 * 是**日环**，"明天下午 14:00"与"今天下午 14:00"算出来是同一个结果。
 * 作息每天重复，所以陈述为真。
 */
enum class TimeHint(
    val label: String,
    /** 相对今天往后几天；承诺就落在那一天的作息覆盖上。 */
    val daysAhead: Int,
    /** 固定目标时刻（当天第几分钟）；null 表示相对"此刻"偏移。 */
    private val fixedMinuteOfDay: Int?,
    private val offsetMinutes: Int,
) {
    NOW("现在", 0, null, 0),
    LATER_TODAY("过会儿", 0, null, 120),
    TONIGHT("今晚", 0, 20 * 60, 0),
    TOMORROW_MORNING("明天早上", 1, 9 * 60, 0),
    TOMORROW_AFTERNOON("明天下午", 1, 14 * 60, 0),
    TOMORROW_EVENING("明天晚上", 1, 19 * 60, 0),
    ;

    /** 目标"当天第几分钟"，按天回绕。 */
    fun resolveMinuteOfDay(nowMinuteOfDay: Int): Int =
        (fixedMinuteOfDay ?: (nowMinuteOfDay + offsetMinutes)).mod(MINUTES_PER_DAY)

    /** 目标日期键（与 `NpcState.todayDateKey` 同一口径，yyyy-MM-dd）。 */
    fun resolveDateKey(now: LocalDate): String = now.plusDays(daysAhead.toLong()).toString()
}

/**
 * 关于"某个时刻他在哪"的**真实**事实，用来把作息变成一句真话。
 *
 * 只允许引用 [placeName] / [activity] / [walking]——模板不许编造别的。
 */
data class ScheduleFacts(
    val targetMinuteOfDay: Int,
    val timeLabel: String,
    val placeName: String,
    val activity: String,
    val walking: Boolean,
    val isSameAsNow: Boolean,
)

/**
 * 求"某个时间提示下他在哪"。作息无效（无有效条目）时返回 null，
 * 此时叙事层不会硬编一句，而是换个说法。
 */
fun scheduleFacts(
    profile: NpcProfile,
    places: Map<String, Place>,
    timeHint: TimeHint,
    nowMinuteOfDay: Int,
): ScheduleFacts? {
    val target = timeHint.resolveMinuteOfDay(nowMinuteOfDay)
    val presence = resolveSchedule(profile, places).presenceAt(target) ?: return null
    return ScheduleFacts(
        targetMinuteOfDay = target,
        timeLabel = timeHint.label,
        placeName = presence.placeName,
        activity = presence.activity,
        walking = presence.walking,
        isSameAsNow = target == nowMinuteOfDay,
    )
}
