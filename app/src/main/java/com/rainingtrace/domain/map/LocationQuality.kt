package com.rainingtrace.domain.map

import com.rainingtrace.domain.track.RecordTrackPointUseCase

/**
 * 定位精度等级。
 *
 * [POOR] 的分界线**就是** [RecordTrackPointUseCase.MAX_ACCURACY_METERS]——
 * 也就是说 `POOR == "这个点正在被丢弃"`。这不是巧合而是刻意对齐：
 * 玩家看到的"信号弱"与系统实际的行为必须是同一件事，否则界面就是在骗人。
 */
enum class LocationQuality {
    /** 十几米以内：可靠，轨迹点会正常落下。 */
    GOOD,

    /** 还能用，但已经在护栏附近了。 */
    FAIR,

    /** 差于精度闸门：轨迹点会被丢弃。 */
    POOR,

    /** 还没拿到定位。 */
    UNKNOWN,
}

/** 良好上限：城市里 GPS 稳定时通常就在这个量级。 */
const val LOCATION_GOOD_MAX_METERS = 15.0

fun locationQualityOf(accuracyMeters: Double?): LocationQuality = when {
    accuracyMeters == null || accuracyMeters.isNaN() -> LocationQuality.UNKNOWN
    accuracyMeters <= LOCATION_GOOD_MAX_METERS -> LocationQuality.GOOD
    accuracyMeters <= RecordTrackPointUseCase.MAX_ACCURACY_METERS -> LocationQuality.FAIR
    else -> LocationQuality.POOR
}