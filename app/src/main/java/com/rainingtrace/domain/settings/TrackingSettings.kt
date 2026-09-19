package com.rainingtrace.domain.settings

/** 后台记录间隔（用户可配置）：越低越准，也越费电。 */
enum class BackgroundInterval(val millis: Long, val label: String) {
    S30(30_000L, "30 秒"),
    M1(60_000L, "1 分钟"),
    M2(120_000L, "2 分钟"),
    M5(300_000L, "5 分钟"),
    ;

    companion object {
        val DEFAULT = M1
    }
}

/** 白天时段预设（只约束后台自动记录；人在前台看地图时不受限）。 */
enum class DayWindow(val startMinuteOfDay: Int, val endMinuteOfDay: Int, val label: String) {
    WIDE(6 * 60, 23 * 60, "06:00 – 23:00"),
    DAY(7 * 60, 22 * 60, "07:00 – 22:00"),
    SHORT(8 * 60, 20 * 60, "08:00 – 20:00"),
    ;

    companion object {
        val DEFAULT = DAY
    }
}

/**
 * 足迹记录设置（GDD §05 足迹 / §22 隐私）。
 *
 * 后台只做一件事：按 [backgroundInterval] 采集位置 → 去噪 → 写 track_points。
 * 迷雾、渲染、世界状态、地点/NPC 交互一律不在后台跑；回前台时按水位增量补算。
 */
data class TrackingSettings(
    /** 显式开关：默认关，开了才启动记录用前台服务。 */
    val enabled: Boolean = false,
    val backgroundInterval: BackgroundInterval = BackgroundInterval.DEFAULT,
    val daytimeOnly: Boolean = true,
    val dayWindow: DayWindow = DayWindow.DEFAULT,
) {
    /** 此刻（本地 0..1439 分钟）是否允许后台记录。 */
    fun allowsRecordingAt(minuteOfDay: Int): Boolean =
        !daytimeOnly || minuteOfDay in dayWindow.startMinuteOfDay until dayWindow.endMinuteOfDay
}