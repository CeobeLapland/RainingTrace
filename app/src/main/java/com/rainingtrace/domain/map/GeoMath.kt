package com.rainingtrace.domain.map

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理距离工具（haversine）。
 *
 * 校园尺度精度足够；仅用于"附近地点"这类玩法查询，
 * 不用于 cell identity（cell identity 必须走 [HexGrid]）。
 */
fun WorldCoordinate.distanceMetersTo(other: WorldCoordinate): Double {
    val r = 6_371_008.883_485_042
    val dLat = Math.toRadians(other.latDegrees - latDegrees)
    val dLng = Math.toRadians(other.lngDegrees - lngDegrees)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(latDegrees)) * cos(Math.toRadians(other.latDegrees)) *
        sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * asin(sqrt(a))
}
