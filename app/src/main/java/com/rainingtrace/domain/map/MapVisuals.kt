package com.rainingtrace.domain.map

import com.rainingtrace.domain.exploration.CellFogState

/**
 * 地图渲染的领域侧视图模型（06_地图专项 §8）。
 *
 * Domain 定义"要画什么"，platform 适配器决定"怎么画"；
 * MapView 等 SDK 类型不允许越过 platform 层。
 */
data class MapCamera(
    val center: WorldCoordinate,
    val zoom: Double,
)

data class HexCellVisual(
    val cellId: HexCellId,
    /** 闭合前的六个顶点（逆时针）。 */
    val polygon: List<WorldCoordinate>,
    val fogState: CellFogState,
)

data class PlayerMarkerVisual(
    val coordinate: WorldCoordinate,
)

enum class MapLayer {
    CELLS,
    PLAYER,
}

interface MapRendererAdapter {
    fun setCamera(camera: MapCamera)
    fun renderCells(cells: List<HexCellVisual>)
    fun renderPlayer(marker: PlayerMarkerVisual?)
    fun clearLayer(layer: MapLayer)
}
