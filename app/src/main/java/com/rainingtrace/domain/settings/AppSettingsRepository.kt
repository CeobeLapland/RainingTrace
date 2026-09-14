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
}
