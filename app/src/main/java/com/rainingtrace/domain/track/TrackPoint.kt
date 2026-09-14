package com.rainingtrace.domain.track

import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.WorldCoordinate

/**
 * 稳定轨迹点：RawLocationFix 经过 RecordTrackPointUseCase 去噪后落库的点。
 *
 * 轨迹点是世界的**唯一空间真相**（战争迷雾架构）：
 * - 迷雾格、今日轨迹线都从它派生；
 * - 只存本地、不上传，默认私密（GDD §22）；
 * - 它不是原始 GPS dump：精度、位移、瞬移均已过滤（06_地图专项 §4/§6）。
 */
data class TrackPoint(
    val id: String,
    val timestampEpochMs: Long,
    val coordinate: WorldCoordinate,
    val accuracyMeters: Double,
    val source: LocationSource,
)

/** 轨迹点仓储：append-only + 时间区间查询（今日轨迹/未来年鉴）。 */
interface TrackRepository {
    suspend fun append(point: TrackPoint)

    suspend fun latestPoint(): TrackPoint?

    /** 时间区间内的点，时间升序。 */
    suspend fun between(fromEpochMs: Long, toEpochMs: Long): List<TrackPoint>

    /** 全部轨迹点（时间升序）；切换迷雾档位时用于整体重建。 */
    suspend fun all(): List<TrackPoint>
}
