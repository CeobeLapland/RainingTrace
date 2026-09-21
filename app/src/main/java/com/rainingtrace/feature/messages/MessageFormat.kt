package com.rainingtrace.feature.messages

import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.domain.npc.NpcMessage
import com.rainingtrace.domain.npc.NpcPresence
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 消息展示用的格式化。会话列表与聊天页共用一份，避免文案漂移。
 *
 * 时间一律走 [WORLD_ZONE]，"今天"由调用方从世界状态传入——UI 不自己取时间。
 */

/** "此刻在「北湖」· 在湖边画画"；走路时改成"正在往「X」走"。 */
internal fun presenceLabel(presence: NpcPresence?): String? {
    if (presence == null) return null
    val where = if (presence.walking) {
        "正在往「${presence.placeName}」走"
    } else {
        "此刻在「${presence.placeName}」"
    }
    return if (presence.activity.isBlank()) where else "$where · ${presence.activity}"
}

/** 会话列表的最后一条预览：自己说的话加个前缀，好区分。 */
internal fun previewOf(message: NpcMessage): String =
    if (message.fromNpc) message.text else "我：${message.text}"

/** 今天只显示时刻，其它日子显示月日。 */
internal fun timeLabel(epochMs: Long, today: LocalDate): String {
    val dateTime = Instant.ofEpochMilli(epochMs).atZone(WORLD_ZONE)
    return if (dateTime.toLocalDate() == today) {
        HH_MM.format(dateTime)
    } else {
        MONTH_DAY.format(dateTime)
    }
}

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
