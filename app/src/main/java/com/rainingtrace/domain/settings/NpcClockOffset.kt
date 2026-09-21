package com.rainingtrace.domain.settings

/**
 * NPC 调试时间偏移——**只影响"NPC 此刻在哪"**，不影响其它任何东西。
 *
 * 为什么不能直接改 [com.rainingtrace.core.time.WorldClock]：`clock.now()` 同时喂
 * 轨迹点时间戳、迷雾水位、记忆创建时间与去噪窗口；偏移会污染轨迹历史，
 * 而且一旦写进库就无法回滚。所以这里只在算 NPC 位置时把"当天第几分钟"挪一下。
 *
 * 为什么需要它：NPC 位置读的是真实 `minuteOfDay`，而调试区只能覆盖"时段"四个桶
 * （两者故意解耦，否则会篡改精确到分钟的条件）。所以想验证"NPC 真的在走路"，
 * 原本只能等到他的行走窗口。挪一下就能立刻看到。
 */
enum class NpcClockOffset(
    val key: String,
    val label: String,
    val minutes: Int,
) {
    NONE("none", "真实时间", 0),
    PLUS_1H("plus_1h", "+1 小时", 60),
    PLUS_3H("plus_3h", "+3 小时", 180),
    PLUS_6H("plus_6h", "+6 小时", 360),
    PLUS_12H("plus_12h", "+12 小时", 720),
    MINUS_3H("minus_3h", "-3 小时", -180),
    ;

    companion object {
        val DEFAULT = NONE

        /** 未知 key 回落 [DEFAULT]，坏数据不该让 NPC 消失。 */
        fun fromKey(key: String): NpcClockOffset = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
