package com.rainingtrace.domain.npc

import com.rainingtrace.domain.world.TimeOfDay
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WorldState

/**
 * 好感与情绪的规则——**全是纯函数**。
 *
 * 这里是"AI 不驱动状态"的落点：不管回复文本由模板还是将来的 LLM 产生，
 * 好感与情绪只由**解析结果**（[ParsedPlayerMessage]）与档案决定。
 */

/** 今天最多能涨多少好感：防止连点发送刷到"朋友"。 */
const val DAILY_AFFECTION_CAP = 5
const val AFFECTION_MAX = 100

/** 情绪从"事件情绪"淡回基线的时间。 */
const val MOOD_DECAY_MS = 30 * 60 * 1000L

private const val AFFECTION_CHAT = 1
private const val AFFECTION_FAVORITE_TOPIC = 2
private const val AFFECTION_CURIOUS_QUESTION = 1

/** 这次聊天该涨多少好感。没听懂就没好感。 */
fun affectionDeltaFor(parsed: ParsedPlayerMessage, profile: NpcProfile): Int {
    if (!parsed.understood) return 0
    var delta = AFFECTION_CHAT
    val favorite = profile.favoriteTopic
    if (favorite != null && favorite in parsed.topics) {
        delta += AFFECTION_FAVORITE_TOPIC
    }
    if (NpcTrait.CURIOUS in profile.traits && parsed.isQuestion) {
        delta += AFFECTION_CURIOUS_QUESTION
    }
    return delta
}

/** 跨日则把"今日已涨"清零（不做别的事——好感本身是累计值）。 */
fun applyDailyReset(state: NpcState, todayDateKey: String): NpcState =
    if (state.todayDateKey == todayDateKey) {
        state
    } else {
        state.copy(todayAffectionGain = 0, todayDateKey = todayDateKey)
    }

/**
 * 结算好感：返回 (新好感, 今日已涨)。
 * 日上限只限制"今天还能涨多少"，不会削减已有好感。
 */
fun affectionAfter(
    current: Int,
    delta: Int,
    todayGain: Int,
    cap: Int = DAILY_AFFECTION_CAP,
): Pair<Int, Int> {
    val allowed = (cap - todayGain).coerceAtLeast(0)
    val granted = delta.coerceAtMost(allowed).coerceAtLeast(0)
    return (current + granted).coerceIn(0, AFFECTION_MAX) to (todayGain + granted)
}

/**
 * 基线情绪：不由对话决定，由"他此刻在不在路上"与世界状态派生。
 * 它不落库——所以情绪会自己淡回去，却不需要任何定时器。
 */
fun baselineMoodOf(presence: NpcPresence?, state: WorldState): NpcMood {
    if (presence?.walking == true) return NpcMood.BUSY
    val wet = state.weather.isRaining || state.weather.kind == WeatherKind.SNOW
    if (state.timeOfDay == TimeOfDay.NIGHT) return if (wet) NpcMood.DOWN else NpcMood.TIRED
    return NpcMood.CALM
}

/** 生效情绪：事件情绪没过期就用它，过期回基线。 */
fun effectiveMood(
    stored: NpcMood,
    moodSinceEpochMs: Long,
    nowEpochMs: Long,
    presence: NpcPresence?,
    state: WorldState,
): NpcMood =
    if (nowEpochMs - moodSinceEpochMs < MOOD_DECAY_MS) stored else baselineMoodOf(presence, state)

/** 这次对话之后该记下什么"事件情绪"；null = 保持现状。 */
fun moodFor(
    parsed: ParsedPlayerMessage,
    profile: NpcProfile,
    presence: NpcPresence?,
): NpcMood? {
    val favorite = profile.favoriteTopic
    return when {
        favorite != null && favorite in parsed.topics -> NpcMood.GLAD
        NpcTrait.CURIOUS in profile.traits && parsed.isQuestion -> NpcMood.INTRIGUED
        presence?.walking == true -> NpcMood.BUSY
        else -> null
    }
}
