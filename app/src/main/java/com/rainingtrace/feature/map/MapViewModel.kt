package com.rainingtrace.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.exploration.MarkCellVisitedUseCase
import com.rainingtrace.domain.exploration.RevealNearbyCellsUseCase
import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.HexCellVisual
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.PlayerMarkerVisual
import com.rainingtrace.domain.map.WorldCoordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapUiState(
    val revealedCount: Int = 0,
    val playerCell: HexCellId? = null,
    val lastFix: WorldCoordinate? = null,
)

/**
 * RT-MAP-002/005/006: 地图屏的单向数据流。
 *
 * 位置流 → cell 映射 → reveal/visit 规则 → ExplorationState
 *   → 视图投影（HexCellVisual）→ MapRendererAdapter。
 * UI 不持有游戏真相（01_技术栈 §4）。
 */
class MapViewModel(
    private val grid: HexGrid,
    private val locationProvider: LocationProvider,
    private val mapRenderer: MapRendererAdapter,
    private val revealNearbyCells: RevealNearbyCellsUseCase,
    private val markCellVisited: MarkCellVisitedUseCase,
    private val clock: WorldClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var explorationState = ExplorationState()
    private var playerCell: HexCellId? = null

    init {
        viewModelScope.launch {
            locationProvider.updates.collect { fix ->
                onLocationFix(fix.coordinate)
            }
        }
    }

    /** Fake 模式：点击地图即"移动到这里"。真实定位接入后由 LocationProvider 驱动。 */
    fun onMapTapped(coordinate: WorldCoordinate) {
        onLocationFix(coordinate)
    }

    fun initialCamera(): MapCamera =
        MapCamera(center = grid.origin, zoom = DEFAULT_ZOOM)

    private fun onLocationFix(coordinate: WorldCoordinate) {
        val cell = grid.cellOf(coordinate)
        playerCell = cell
        explorationState = revealNearbyCells(explorationState, cell, REVEAL_RADIUS)
        explorationState = markCellVisited(explorationState, cell)
        renderAround(cell)
        mapRenderer.renderPlayer(PlayerMarkerVisual(grid.cellCenter(cell)))
        _uiState.value = MapUiState(
            revealedCount = explorationState.revealedCount(),
            playerCell = cell,
            lastFix = coordinate,
        )
    }

    /** 渲染玩家周边窗口：未知格画成灰色迷雾，已知格按状态着色。 */
    private fun renderAround(center: HexCellId) {
        val visuals = grid.cellsWithinRadius(center, RENDER_RADIUS).map { cell ->
            HexCellVisual(
                cellId = cell,
                polygon = grid.cellPolygon(cell),
                fogState = explorationState.stateOf(cell),
            )
        }
        mapRenderer.renderCells(visuals)
    }

    companion object {
        const val REVEAL_RADIUS = 2
        const val RENDER_RADIUS = 6
        const val DEFAULT_ZOOM = 16.5
    }
}
