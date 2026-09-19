package com.rainingtrace.domain.track

import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln

/** 轨迹/日记统一时间口径：与全局世界时区同一个值（见 [WORLD_ZONE]）。 */
val TRACK_ZONE: ZoneId = WORLD_ZONE
const val TRACK_ZONE_OFFSET_MS = 8L * 60 * 60 * 1000
const val DAY_MS = 24L * 60 * 60 * 1000

/** 本地日起点（含）。 */
fun dayStartEpochMs(date: LocalDate): Long = date.toEpochDay() * DAY_MS - TRACK_ZONE_OFFSET_MS

/** 本地日终点（不含，即次日零点）。 */
fun dayEndEpochMs(date: LocalDate): Long = dayStartEpochMs(date.plusDays(1))

/** 按本地时区偏移把时间戳分桶成"日序号"，供 SQL GROUP BY 使用。 */
fun localDayIndex(epochMs: Long, zoneOffsetMs: Long = TRACK_ZONE_OFFSET_MS): Long =
    Math.floorDiv(epochMs + zoneOffsetMs, DAY_MS)

/**
 * 某天有轨迹的汇总（轨迹日历的一格）。
 * 距离/时长这类需要遍历点的量不在这里算，选中那天时再按需加载。
 */
data class TrackDay(
    val date: LocalDate,
    val pointCount: Int,
    val firstEpochMs: Long,
    val lastEpochMs: Long,
)

fun trackDayOf(dayIndex: Long, pointCount: Int, firstEpochMs: Long, lastEpochMs: Long): TrackDay =
    TrackDay(LocalDate.ofEpochDay(dayIndex), pointCount, firstEpochMs, lastEpochMs)

/** 轨迹总长度（米）：相邻点距离累加（06_地图专项 §6 的"走过哪里"）。 */
fun trackLengthMeters(points: List<TrackPoint>): Double =
    points.zipWithNext().sumOf { (a, b) -> a.coordinate.distanceMetersTo(b.coordinate) }

/**
 * 让整段轨迹落进屏幕的相机（无点时 null）。
 *
 * 用固定参考屏宽估算缩放：这个近似只服务于"跳过去能看全"，不需要精确贴合。
 */
fun trackCamera(
    points: List<TrackPoint>,
    referenceScreenPx: Double = 1080.0,
    padding: Double = 1.35,
): MapCamera? {
    if (points.isEmpty()) return null
    val minLat = points.minOf { it.coordinate.latDegrees }
    val maxLat = points.maxOf { it.coordinate.latDegrees }
    val minLng = points.minOf { it.coordinate.lngDegrees }
    val maxLng = points.maxOf { it.coordinate.lngDegrees }
    val center = WorldCoordinate((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)

    val latRad = Math.toRadians(center.latDegrees)
    val latSpanM = (maxLat - minLat) * METERS_PER_DEGREE_LAT
    val lngSpanM = (maxLng - minLng) * METERS_PER_DEGREE_LAT * cos(latRad)
    val spanM = hypot(latSpanM, lngSpanM) * padding

    val metersPerPixelAtZoom0 = METERS_PER_PIXEL_AT_ZOOM0 * cos(latRad)
    val zoom = if (spanM < 1.0) {
        MAX_ZOOM
    } else {
        ln(metersPerPixelAtZoom0 * referenceScreenPx / spanM) / LN_2
    }
    return MapCamera(center, zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
}

private const val METERS_PER_DEGREE_LAT = 111_320.0
private const val METERS_PER_PIXEL_AT_ZOOM0 = 156_543.03392
private const val MIN_ZOOM = 12.0
private const val MAX_ZOOM = 18.5
private const val LN_2 = 0.6931471805599453