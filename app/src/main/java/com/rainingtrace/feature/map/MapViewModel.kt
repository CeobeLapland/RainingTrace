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
import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.HexCellVisual
import com.rainingtrace.domain.map.GridManager
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.MapLayer
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.MapViewport
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceVisual
import com.rainingtrace.domain.map.PlayerMarkerVisual
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.track.RecordTrackPointUseCase
import com.rainingtrace.domain.track.RecordTrackResult
import com.rainingtrace.domain.track.RevealFogFromPointUseCase
import com.rainingtrace.domain.track.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId

enum class LocationPermission { UNKNOWN, GRANTED, DENIED }

data class MapUiState(
    val revealedCount: Int = 0,
    val lastFix: WorldCoordinate? = null,
    /** 已选中的地点（点图标/附近列表选中）→ 详情卡。 */
    val selectedPlace: Place? = null,
    /** 观察范围内的全部地点（按距离升序），驱动"附近多地点"列表。 */
    val nearbyPlaces: List<Place> = emptyList(),
    val toast: String? = null,
    val showTrack: Boolean = true,
    val showFog: Boolean = true,
    val locationMode: LocationMode = LocationMode.FAKE,
    val locationPermission: LocationPermission = LocationPermission.UNKNOWN,
)

/**
 * 地图屏单向数据流（战争迷雾架构）：
 *
 * 位置流 → 去噪落轨迹点（空间真相）→ 米制半径开雾（投影）→ 持久化 → 渲染；
 * 迷雾/格染色只渲染与相机视口相交的格（未知格铺浓雾）；
 * 今日轨迹从轨迹点表查询；地点是连续坐标，不写格子状态。
 */
class MapViewModel(
    private val clock: WorldClock,
    private val gridManager: GridManager,
    private val locationProvider: LocationProvider,
    private val mapRenderer: MapRendererAdapter,
    private val recordTrackPoint: RecordTrackPointUseCase,
    private val revealFog: RevealFogFromPointUseCase,
    private val trackRepository: TrackRepository,
    private val observePlace: ObservePlaceUseCase,
    private val placeRepository: PlaceRepository,
    private val explorationRepository: ExplorationRepository,
    /** Fake 模式下点击地图移动；GPS 模式内部忽略。 */
    private val debugMapTap: ((WorldCoordinate) -> Unit)?,
    private val locationModeFlow: Flow<LocationMode>,
    /** 权限授予后重新拉起 GPS 采集。 */
    private val refreshLocation: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var explorationState = ExplorationState()
    private var lastCoordinate: WorldCoordinate? = null
    private var viewport: MapViewport? = null

    init {
        viewModelScope.launch {
            explorationState = explorationRepository.loadState()
            mapRenderer.setLayerVisible(MapLayer.TRACK, _uiState.value.showTrack)
            mapRenderer.setLayerVisible(MapLayer.FOG_MASK, _uiState.value.showFog)
            refreshTodayTrack()
            launch {
                locationModeFlow.collect { mode ->
                    _uiState.value = _uiState.value.copy(locationMode = mode)
                }
            }
            locationProvider.updates.collect { fix ->
                onLocationFix(fix)
            }
        }
    }

    /** Compose 权限请求结果回调；授权后重新拉起 GPS 采集。 */
    fun onPermissionResult(granted: Boolean) {
        val permission = if (granted) LocationPermission.GRANTED else LocationPermission.DENIED
        _uiState.value = _uiState.value.copy(locationPermission = permission)
        if (granted) refreshLocation()
    }

    /** Fake 模式：点击地图 = 移动（注入 FakeLocationProvider）；真实模式无效。 */
    fun onMapTapped(coordinate: WorldCoordinate) {
        debugMapTap?.invoke(coordinate)
    }

    /** 相机停止移动：按新视口重渲染迷雾。 */
    fun onViewportChanged(viewport: MapViewport) {
        this.viewport = viewport
        renderViewport()
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
            renderViewport()
            lastCoordinate?.let { coordinate ->
                mapRenderer.renderPlayer(PlayerMarkerVisual(coordinate))
                refreshPlaces(coordinate)
            }
            refreshTodayTrack()
            mapRenderer.setLayerVisible(MapLayer.TRACK, _uiState.value.showTrack)
            mapRenderer.setLayerVisible(MapLayer.FOG_MASK, _uiState.value.showFog)
        }
    }

    fun setShowTrack(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showTrack = visible)
        mapRenderer.setLayerVisible(MapLayer.TRACK, visible)
    }

    fun setShowFog(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showFog = visible)
        mapRenderer.setLayerVisible(MapLayer.FOG_MASK, visible)
    }

    fun onObserveClicked() {
        val place = _uiState.value.selectedPlace ?: return
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

    /** 点地图地点图标：按 id 解析并选中（弹出详情卡）。 */
    fun onPlaceTapped(placeId: String) {
        viewModelScope.launch {
            placeRepository.placeById(placeId)?.let { selectPlace(it) }
        }
    }

    /** 选中地点（详情卡）；重复选同一地点则收起。 */
    fun selectPlace(place: Place) {
        _uiState.value = if (_uiState.value.selectedPlace?.id == place.id) {
            _uiState.value.copy(selectedPlace = null)
        } else {
            _uiState.value.copy(selectedPlace = place)
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPlace = null)
    }

    fun consumeToast() {
        _uiState.value = _uiState.value.copy(toast = null)
    }

    fun initialCamera(): MapCamera =
        MapCamera(center = gridManager.grid.origin, zoom = DEFAULT_ZOOM)

    private suspend fun onLocationFix(fix: com.rainingtrace.domain.map.RawLocationFix) {
        // 去噪闸门：只有稳定点才成为轨迹、才开雾。被拒的漂移点不移动玩家。
        val result = recordTrackPoint(fix)
        if (result !is RecordTrackResult.Accepted) return

        val coordinate = result.point.coordinate
        lastCoordinate = coordinate

        explorationState = revealFog(explorationState, coordinate)
        explorationRepository.saveStates(explorationState.cellStates)
        renderViewport()
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
        mapRenderer.renderPlaces(places.map { PlaceVisual(it.id, it.name, it.coordinate, it.type) })
        _uiState.value = _uiState.value.copy(
            nearbyPlaces = places.filter {
                it.coordinate.distanceMetersTo(coordinate) <= PLACE_CARD_RADIUS_METERS
            },
        )
    }

    /**
     * 视口驱动渲染（分块掩膜）：
     * 枚举与外扩视口相交的**全部**格——未知格渲染浓雾，已知格按状态渲染
     * 薄雾/染色。格数随视口走，与世界大小无关（最小缩放由 MapLibre 端限制）。
     */
    private fun renderViewport() {
        val vp = viewport ?: return
        val grid = gridManager.grid
        val outer = vp.expanded(FOG_EXPAND_FACTOR)
        val covering = grid.cellsInRect(
            minLat = outer.southWest.latDegrees,
            minLng = outer.southWest.lngDegrees,
            maxLat = outer.northEast.latDegrees,
            maxLng = outer.northEast.lngDegrees,
        )
        mapRenderer.renderCells(
            covering.map { cell ->
                HexCellVisual(
                    cellId = cell,
                    polygon = grid.cellPolygon(cell),
                    fogState = explorationState.stateOf(cell),
                )
            },
        )
    }

    private fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toast = message)
    }

    companion object {
        const val DEFAULT_ZOOM = 16.5
        const val PLACE_MARKER_RADIUS_METERS = 600.0
        const val PLACE_CARD_RADIUS_METERS = 150.0
        private const val FOG_EXPAND_FACTOR = 1.35
        private val TRACK_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
