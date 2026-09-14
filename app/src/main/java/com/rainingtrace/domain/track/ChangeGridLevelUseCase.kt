package com.rainingtrace.domain.track

import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.map.GridLevel
import com.rainingtrace.domain.map.GridManager
import com.rainingtrace.domain.settings.AppSettingsRepository

/**
 * 切换六边形格子档位。
 *
 * 轨迹点是唯一空间真相，迷雾只是当前档位上的投影，所以换档 =
 * 持久化偏好 → 切 GridManager → 清旧档位迷雾 → 从全部轨迹点重建 → 落库。
 * 轨迹线、地点、记忆的位置（连续坐标）完全不受影响。
 */
class ChangeGridLevelUseCase(
    private val gridManager: GridManager,
    private val settings: AppSettingsRepository,
    private val explorationRepository: ExplorationRepository,
    private val trackRepository: TrackRepository,
    private val rebuild: RebuildFogFromTrackUseCase,
) {
    suspend operator fun invoke(level: GridLevel) {
        if (level == gridManager.level) return

        settings.setGridLevel(level)
        gridManager.select(level)
        explorationRepository.clearLevel()

        val state = rebuild(trackRepository.all())
        explorationRepository.saveStates(state.cellStates)
    }
}
