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

/**
 * 两个坐标之间的直线插值（[t] = 0 取自己，1 取 [other]，超出范围夹紧）。
 *
 * 用于 NPC 在两地之间"走路"这种校园尺度的短距离插值：几百米内经纬度线性插值
 * 与等距圆柱投影的差异是亚米级，而它不依赖 [HexGrid]（网格的 cellSize 可切档，
 * NPC 坐标不该随档位变化）。不处理反经线。
 */
fun WorldCoordinate.lerpTo(other: WorldCoordinate, t: Double): WorldCoordinate {
    val ratio = t.coerceIn(0.0, 1.0)
    return WorldCoordinate(
        latDegrees = latDegrees + (other.latDegrees - latDegrees) * ratio,
        lngDegrees = lngDegrees + (other.lngDegrees - lngDegrees) * ratio,
    )
}
