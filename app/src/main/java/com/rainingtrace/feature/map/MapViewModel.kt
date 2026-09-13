package com.rainingtrace.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.exploration.MarkCellVisitedUseCase
import com.rainingtrace.domain.exploration.ObservePlaceUseCase
import com.rainingtrace.domain.exploration.ObserveRejectReason
import com.rainingtrace.domain.exploration.ObserveResult
import com.rainingtrace.domain.exploration.RevealNearbyCellsUseCase
import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.HexCellVisual
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceVisual
import com.rainingtrace.domain.map.PlayerMarkerVisual
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapUiState(
    val revealedCount: Int = 0,
    val playerCell: HexCellId? = null,
    val lastFix: WorldCoordinate? = null,
    val nearbyPlace: Place? = null,
    val toast: String? = null,
)

/**
 * 地图屏单向数据流：
 * 位置流 → cell 映射 → reveal/visit → 持久化 → 渲染；
 * 附近地点查询 → 观察动作 → 资源/足迹。
 */
class MapViewModel(
    private val grid: HexGrid,
    private val locationProvider: LocationProvider,
    private val mapRenderer: MapRendererAdapter,
    private val revealNearbyCells: RevealNearbyCellsUseCase,
    private val markCellVisited: MarkCellVisitedUseCase,
    private val observePlace: ObservePlaceUseCase,
    private val placeRepository: PlaceRepository,
    private val explorationRepository: ExplorationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var explorationState = ExplorationState()
    private var playerCell: HexCellId? = null
    private var lastCoordinate: WorldCoordinate? = null

    init {
        // 顺序保证：先恢复持久化探索状态（重启数据还在），再接位置流。
        viewModelScope.launch {
            explorationState = explorationRepository.loadState()
            playerCell?.let { renderCellsAround(it) }
            locationProvider.updates.collect { fix ->
                onLocationFix(fix.coordinate)
            }
        }
    }

    /** Fake 模式：点击地图即"移动到这里"。 */
    fun onMapTapped(coordinate: WorldCoordinate) {
        onLocationFix(coordinate)
    }

    /**
     * 从其他屏返回时刷新：重载持久化状态并重渲染。
     * 只允许在地图 attach 完成后调用（MapScreen 触发），
     * 否则会写到已销毁的旧 style 上导致崩溃。
     */
    fun refresh() {
        viewModelScope.launch {
            explorationState = explorationRepository.loadState()
            _uiState.value = _uiState.value.copy(
                revealedCount = explorationState.revealedCount(),
            )
            playerCell?.let { cell ->
                renderCellsAround(cell)
                lastCoordinate?.let { refreshPlaces(it) }
            }
        }
    }

    fun onObserveClicked() {
        val place = _uiState.value.nearbyPlace ?: return
        val coordinate = lastCoordinate ?: return
        viewModelScope.launch {
            when (val result = observePlace(coordinate, place)) {
                is ObserveResult.Success ->
                    showToast("获得「${result.resourceName}」×1（共 ${result.newQuantity}）")
                is ObserveResult.Rejected -> showToast(
                    when (result.reason) {
                        ObserveRejectReason.TOO_FAR -> "离地点太远了"
                        ObserveRejectReason.ON_COOLDOWN -> "刚观察过，让它安静一会儿"
                        ObserveRejectReason.ACTION_NOT_AVAILABLE -> "这里没什么可观察的"
                        ObserveRejectReason.REWARD_FAILED -> "观察失败了"
                    },
                )
            }
        }
    }

    fun consumeToast() {
        _uiState.value = _uiState.value.copy(toast = null)
    }

    fun initialCamera(): MapCamera =
        MapCamera(center = grid.origin, zoom = DEFAULT_ZOOM)

    private fun onLocationFix(coordinate: WorldCoordinate) {
        lastCoordinate = coordinate
        val cell = grid.cellOf(coordinate)
        playerCell = cell
        explorationState = revealNearbyCells(explorationState, cell, REVEAL_RADIUS)
        explorationState = markCellVisited(explorationState, cell)
        renderCellsAround(cell)
        mapRenderer.renderPlayer(PlayerMarkerVisual(grid.cellCenter(cell)))
        _uiState.value = _uiState.value.copy(
            revealedCount = explorationState.revealedCount(),
            playerCell = cell,
            lastFix = coordinate,
        )
        viewModelScope.launch { refreshPlaces(coordinate) }
    }

    private suspend fun refreshPlaces(coordinate: WorldCoordinate) {
        val places = placeRepository.nearby(coordinate, PLACE_MARKER_RADIUS_METERS)
        renderPlaceMarkers(places)
        // 已发现的地点格标为 SPECIAL（紫色），成为世界图的一部分
        places.forEach { place ->
            explorationState = explorationState.withState(
                grid.cellOf(place.coordinate),
                CellFogState.SPECIAL,
            )
        }
        playerCell?.let { renderCellsAround(it) }
        explorationRepository.saveStates(explorationState.cellStates)
        _uiState.value = _uiState.value.copy(
            nearbyPlace = places.firstOrNull {
                it.coordinate.distanceMetersTo(coordinate) <= PLACE_CARD_RADIUS_METERS
            },
        )
    }

    private fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toast = message)
    }

    private fun renderCellsAround(center: HexCellId) {
        val visuals = grid.cellsWithinRadius(center, RENDER_RADIUS).map { cell ->
            HexCellVisual(
                cellId = cell,
                polygon = grid.cellPolygon(cell),
                fogState = explorationState.stateOf(cell),
            )
        }
        mapRenderer.renderCells(visuals)
    }

    private fun renderPlaceMarkers(places: List<Place>) {
        mapRenderer.renderPlaces(places.map { PlaceVisual(it.id, it.name, it.coordinate) })
    }

    companion object {
        const val REVEAL_RADIUS = 2
        const val RENDER_RADIUS = 6
        const val DEFAULT_ZOOM = 16.5
        const val PLACE_MARKER_RADIUS_METERS = 600.0
        const val PLACE_CARD_RADIUS_METERS = 150.0
    }
}
