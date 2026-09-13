package com.rainingtrace.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.exploration.ObservePlaceUseCase
import com.rainingtrace.domain.exploration.ObserveRejectReason
import com.rainingtrace.domain.exploration.ObserveResult
import com.rainingtrace.domain.map.HexCellVisual
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.MapLayer
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceVisual
import com.rainingtrace.domain.map.PlayerMarkerVisual
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.track.RecordTrackPointUseCase
import com.rainingtrace.domain.track.RecordTrackResult
import com.rainingtrace.domain.track.RevealFogFromPointUseCase
import com.rainingtrace.domain.track.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

data class MapUiState(
    val revealedCount: Int = 0,
    val lastFix: WorldCoordinate? = null,
    val nearbyPlace: Place? = null,
    val toast: String? = null,
    val showTrack: Boolean = true,
)

/**
 * 地图屏单向数据流（战争迷雾架构）：
 *
 * 位置流 → 去噪落轨迹点（空间真相）→ 米制半径开雾（投影）→ 持久化 → 渲染；
 * 今日轨迹从轨迹点表查询；附近地点与观察动作不变（连续坐标、不吸附格子）。
 */
class MapViewModel(
    private val clock: WorldClock,
    private val grid: HexGrid,
    private val locationProvider: LocationProvider,
    private val mapRenderer: MapRendererAdapter,
    private val recordTrackPoint: RecordTrackPointUseCase,
    private val revealFog: RevealFogFromPointUseCase,
    private val trackRepository: TrackRepository,
    private val observePlace: ObservePlaceUseCase,
    private val placeRepository: PlaceRepository,
    private val explorationRepository: ExplorationRepository,
    /** Fake 模式下点击地图移动；真实定位模式为 null。 */
    private val debugMapTap: ((WorldCoordinate) -> Unit)?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var explorationState = ExplorationState()
    private var lastCoordinate: WorldCoordinate? = null

    init {
        viewModelScope.launch {
            explorationState = explorationRepository.loadState()
            renderAllKnownCells()
            refreshTodayTrack()
            mapRenderer.setLayerVisible(MapLayer.TRACK, _uiState.value.showTrack)
            locationProvider.updates.collect { fix ->
                onLocationFix(fix)
            }
        }
    }

    /** Fake 模式：点击地图 = 移动（注入 FakeLocationProvider）；真实模式无效。 */
    fun onMapTapped(coordinate: WorldCoordinate) {
        debugMapTap?.invoke(coordinate)
    }

    /**
     * 从其他屏/Tab 返回：MapView 已重建，重载持久化状态并补渲染。
     */
    fun refresh() {
        viewModelScope.launch {
            explorationState = explorationRepository.loadState()
            _uiState.value = _uiState.value.copy(
                revealedCount = explorationState.revealedCount(),
            )
            renderAllKnownCells()
            lastCoordinate?.let { coordinate ->
                mapRenderer.renderPlayer(PlayerMarkerVisual(coordinate))
                refreshPlaces(coordinate)
            }
            refreshTodayTrack()
            mapRenderer.setLayerVisible(MapLayer.TRACK, _uiState.value.showTrack)
        }
    }

    fun setShowTrack(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showTrack = visible)
        mapRenderer.setLayerVisible(MapLayer.TRACK, visible)
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

    private suspend fun onLocationFix(fix: com.rainingtrace.domain.map.RawLocationFix) {
        // 去噪闸门：只有稳定点才成为轨迹、才开雾。被拒的漂移点不移动玩家。
        val result = recordTrackPoint(fix)
        if (result !is RecordTrackResult.Accepted) return

        val coordinate = result.point.coordinate
        lastCoordinate = coordinate

        explorationState = revealFog(explorationState, coordinate)
        explorationRepository.saveStates(explorationState.cellStates)
        renderAllKnownCells()
        mapRenderer.renderPlayer(PlayerMarkerVisual(coordinate))
        refreshTodayTrack()
        refreshPlaces(coordinate)

        _uiState.value = _uiState.value.copy(
            revealedCount = explorationState.revealedCount(),
            lastFix = coordinate,
        )
    }

    private suspend fun refreshTodayTrack() {
        val nowInstant = clock.now()
        val dayStartMs = nowInstant.atZone(TRACK_ZONE)
            .toLocalDate()
            .atStartOfDay(TRACK_ZONE)
            .toInstant()
            .toEpochMilli()
        val points = trackRepository.between(dayStartMs, nowInstant.toEpochMilli())
            .map { it.coordinate }
        mapRenderer.renderTrack(points)
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
        renderAllKnownCells()
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

    private fun renderAllKnownCells() {
        // S1：全量推送已知格（校园尺度量级可接受）；S2 改为相机视口驱动。
        val visuals = explorationState.cellStates.map { (cell, fog) ->
            HexCellVisual(
                cellId = cell,
                polygon = grid.cellPolygon(cell),
                fogState = fog,
            )
        }
        mapRenderer.renderCells(visuals)
    }

    private fun renderPlaceMarkers(places: List<Place>) {
        mapRenderer.renderPlaces(places.map { PlaceVisual(it.id, it.name, it.coordinate) })
    }

    companion object {
        const val DEFAULT_ZOOM = 16.5
        const val PLACE_MARKER_RADIUS_METERS = 600.0
        const val PLACE_CARD_RADIUS_METERS = 150.0
        private val TRACK_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
