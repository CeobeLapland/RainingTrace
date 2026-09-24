package com.rainingtrace.core.ui

import com.rainingtrace.domain.map.LocationQuality
import com.rainingtrace.domain.map.locationQualityOf
import kotlin.math.roundToInt

fun LocationQuality.label(): String = when (this) {
    LocationQuality.GOOD -> "信号好"
    LocationQuality.FAIR -> "信号一般"
    LocationQuality.POOR -> "信号弱"
    LocationQuality.UNKNOWN -> "信号未知"
}

/**
 * GPS 状态 chip 里"定位中"后面的那半句；null = 还没拿到定位，只说"定位中"就好。
 *
 * [LocationQuality.POOR] 时**刻意不给米数**：那个数字会让人以为"再等一会儿就好了"，
 * 而实际上此刻的轨迹点正在被丢弃。直接说清后果比给一个精确但误导的数字有用。
 */
fun gpsQualitySuffix(accuracyMeters: Double?): String? {
    val accuracy = accuracyMeters ?: return null
    return when (locationQualityOf(accuracy)) {
        LocationQuality.GOOD, LocationQuality.FAIR -> "精度 ±${accuracy.roundToInt()} m"
        LocationQuality.POOR -> "信号弱，暂时不记轨迹"
        LocationQuality.UNKNOWN -> null
    }
}