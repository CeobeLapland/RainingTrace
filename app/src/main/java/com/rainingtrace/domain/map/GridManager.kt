package com.rainingtrace.domain.map

/**
 * 当前六边形网格的持有者与切换器。
 *
 * 迷雾是轨迹点在"当前档位网格"上的投影，切换档位后由
 * ChangeGridLevelUseCase 从轨迹点整体重建迷雾。
 * 所有需要现算格子的地方（开雾、记忆、地图渲染）都从这里取 grid，
 * 不允许长期缓存某个档位的 HexGrid。
 */
class GridManager(
    initialLevel: GridLevel = GridLevel.DEFAULT,
    private val origin: WorldCoordinate,
) {
    @Volatile
    var level: GridLevel = initialLevel
        private set

    @Volatile
    var grid: HexGrid = HexGrid(origin, initialLevel.cellSizeMeters)
        private set

    fun select(level: GridLevel) {
        if (level == this.level) return
        this.level = level
        this.grid = HexGrid(origin, level.cellSizeMeters)
    }
}
