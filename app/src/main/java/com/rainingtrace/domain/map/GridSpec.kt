package com.rainingtrace.domain.map

/**
 * 六边形格子尺寸档位。
 *
 * 六边形只是迷雾的表现网格；轨迹点、地点、记忆都以连续坐标为真相，
 * 切换档位后迷雾可从轨迹点整体重建。
 */
enum class GridLevel(
    val key: String,
    val cellSizeMeters: Double,
    val label: String,
) {
    S("s", 25.0, "精细 25m"),
    M("m", 40.0, "标准 40m"),
    L("l", 60.0, "宽松 60m"),
    XL("xl", 100.0, "概览 100m"),
    ;

    /** 持久化在 exploration_cells 主键上的档位前缀，如 "gm:12:-7"。 */
    val cellKeyPrefix: String get() = "g$key:"

    companion object {
        val DEFAULT = M

        fun fromKey(key: String): GridLevel? = entries.firstOrNull { it.key == key }
    }
}
