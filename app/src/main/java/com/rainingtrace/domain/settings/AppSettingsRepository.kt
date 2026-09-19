package com.rainingtrace.domain.settings

import com.rainingtrace.domain.map.GridLevel
import kotlinx.coroutines.flow.Flow

/**
 * 本地偏好（DataStore 实现）：格子档位、定位模式、图层开关默认值。
 * 只存本机，不上传。
 */
interface AppSettingsRepository {
    val gridLevel: Flow<GridLevel>
    suspend fun currentGridLevel(): GridLevel
    suspend fun setGridLevel(level: GridLevel)

    val locationMode: Flow<LocationMode>
    suspend fun currentLocationMode(): LocationMode
    suspend fun setLocationMode(mode: LocationMode)

    /** 地图图层筛选（地点类型/记忆/记忆时间），重启保留。 */
    val mapFilter: Flow<MapFilterSettings>
    suspend fun currentMapFilter(): MapFilterSettings
    suspend fun setMapFilter(filter: MapFilterSettings)

    /** 足迹记录（后台低频记录）设置。 */
    val tracking: Flow<TrackingSettings>
    suspend fun currentTracking(): TrackingSettings
    suspend fun setTracking(settings: TrackingSettings)

    /**
     * 迷雾水位：最后一条已投影到迷雾的轨迹时间戳（本机记账，不是用户偏好）。
     *
     * 后台只写轨迹点、不算迷雾；回前台按水位把新增轨迹点增量补算一次，
     * 这样"熄屏走过的一段路"回到前台就自动开雾，而不必在后台跑渲染。
     */
    suspend fun fogWatermarkMs(): Long?
    suspend fun setFogWatermarkMs(epochMs: Long)
}
