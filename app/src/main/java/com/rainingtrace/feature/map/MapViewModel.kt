package com.rainingtrace.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.core.lifecycle.AppForegroundState
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.exploration.ObservePlaceUseCase
import com.rainingtrace.domain.exploration.ObserveRejectReason
import com.rainingtrace.domain.exploration.ObserveResult
import com.rainingtrace.domain.exploration.rank
import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.HexCellVisual
import com.rainingtrace.domain.map.GridManager
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.MapLayer
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.MapViewport
import com.rainingtrace.domain.map.MemoryVisual
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.PlaceVisual
import com.rainingtrace.domain.map.PlayerMarkerVisual
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.memory.MemoryFocusRequest
import com.rainingtrace.domain.memory.MemoryNode
import com.rainingtrace.domain.memory.MemoryRepository
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.settings.MapFilterSettings
import com.rainingtrace.domain.settings.MemoryTimeFilter
import com.rainingtrace.domain.track.RecordTrackPointUseCase
import com.rainingtrace.domain.track.RecordTrackResult
import com.rainingtrace.domain.track.RevealFogFromPointUseCase
import com.rainingtrace.domain.track.TRACK_ZONE
import com.rainingtrace.domain.track.TrackDayFocusRequest
import com.rainingtrace.domain.track.TrackPoint
import com.rainingtrace.domain.track.TrackRepository
import com.rainingtrace.domain.track.dayEndEpochMs
import com.rainingtrace.domain.track.dayStartEpochMs
import com.rainingtrace.domain.track.trackCamera
import com.rainingtrace.domain.track.trackLengthMeters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

enum class LocationPermission { UNKNOWN, GRANTED, DENIED }

data class MapUiState(
    val revealedCount: Int = 0,
    val lastFix: WorldCoordinate? = null,
    /** 已选中的地点（点图标/附近列表选中）→ 详情卡。 */
    val selectedPlace: Place? = null,
    /** 日记「在地图查看」跳过来的记忆 → 聚焦卡 + 高亮环。 */
    val focusedMemory: MemoryNode? = null,
    /** 轨迹日历跳过来的某一天 → 底部摘要卡 + 画那天的轨迹（空 = 画今天）。 */
    val focusedTrackDay: FocusedTrackDay? = null,
    /** 观察范围内的已揭示地点（按距离升序），驱动"附近多地点"列表。 */
    val nearbyPlaces: List<Place> = emptyList(),
    val showFilterPanel: Boolean = false,
    val toast: String? = null,
    val showTrack: Boolean = true,
    val showFog: Boolean = true,
    val locationMode: LocationMode = LocationMode.FAKE,
    val locationPermission: LocationPermission = LocationPermission.UNKNOWN,
    /** 足迹记录开关打开（GPS 模式下才有意义）→ 左上角"记录中"提示。 */
    val trackingEnabled: Boolean = false,
)

/** 轨迹日历选中的一天：摘要文案已算好，UI 不做业务计算。 */
data class FocusedTrackDay(
    val date: LocalDate,
    val pointCount: Int,
    val lengthMeters: Double,
    val startLabel: String,
    val endLabel: String,
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
    private val memoryRepository: MemoryRepository,
    /** 日记「在地图查看」的一次性聚焦请求。 */
    private val memoryFocus: MemoryFocusRequest,
    /** 轨迹日历「在地图查看」的一次性聚焦请求。 */
    private val trackDayFocus: TrackDayFocusRequest,
    /** 前后台状态：后台不跑迷雾/渲染/查地点（省电边界）。 */
    private val foregroundState: AppForegroundState,
    /** Fake 模式下点击地图移动；GPS 模式内部忽略。 */
    private val debugMapTap: ((WorldCoordinate) -> Unit)?,
    /** 本地偏好：定位模式 + 图层筛选（筛选作为唯一真相，重启保留）。 */
    private val settings: AppSettingsRepository,
    /** 权限授予后重新拉起 GPS 采集。 */
    private val refreshLocation: () -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /** 图层筛选当前值（DataStore 为唯一真相，UI 从这里读）。 */
    private val _filters = MutableStateFlow(MapFilterSettings())
    val filters: StateFlow<MapFilterSettings> = _filters.asStateFlow()

    private var explorationState = ExplorationState()
    private var lastCoordinate: WorldCoordinate? = null
    private var viewport: MapViewport? = null

    init {
        viewModelScope.launch {
            explorationState = explorationRepository.loadState()
            mapRenderer.setLayerVisible(MapLayer.TRACK, _uiState.value.showTrack)
            mapRenderer.setLayerVisible(MapLayer.FOG_MASK, _uiState.value.showFog)
            refreshTrack()
            launch {
                settings.locationMode.collect { mode ->
                    _uiState.value = _uiState.value.copy(locationMode = mode)
                }
            }
            // 筛选唯一真相在 DataStore：变化即重渲染（含重启恢复）。
            launch {
                settings.mapFilter.collect { f ->
                    _filters.value = f
                    renderMemoriesNow()
                    lastCoordinate?.let { refreshPlaces(it) }
                }
            }
            // 日记「在地图查看」：把相机移到该记忆并高亮。
            launch {
                memoryFocus.memory.collect { memory ->
                    if (memory != null) focusMemory(memory)
                }
            }
            // 轨迹日历「在地图查看」：把相机移到那天轨迹的范围。
            launch {
                trackDayFocus.date.collect { date ->
                    if (date != null) focusTrackDay(date)
                }
            }
            // 足迹记录开关：只用于左上角提示。
            launch {
                settings.tracking.collect { tracking ->
                    _uiState.value = _uiState.value.copy(trackingEnabled = tracking.enabled)
                }
            }
            locationProvider.updates.collect { fix ->
                // 前台闸门：进程不可见时直接丢弃。
                // 后台只由 TrackRecordingService 写轨迹点，这里不跑去噪/开雾/渲染/查地点。
                if (!foregroundState.isForeground.value) return@collect
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
            // 后台只写了轨迹点、没算迷雾：回到前台按水位一次性补算。
            catchUpFogFromBackgroundTracks()
            _uiState.value = _uiState.value.copy(
                revealedCount = explorationState.revealedCount(),
            )
            renderViewport()
            lastCoordinate?.let { coordinate ->
                mapRenderer.renderPlayer(PlayerMarkerVisual(coordinate))
                refreshPlaces(coordinate)
            }
            refreshTrack()
            mapRenderer.setLayerVisible(MapLayer.TRACK, _uiState.value.showTrack)
            mapRenderer.setLayerVisible(MapLayer.FOG_MASK, _uiState.value.showFog)
            // MapView 重建后高亮环也没了，补一次。
            _uiState.value.focusedMemory?.let { mapRenderer.renderFocus(it.coordinate) }
        }
    }

    /**
     * 后台记录 → 前台补算迷雾。
     *
     * 后台服务只往 track_points 写点；迷雾是轨迹点的投影，回到前台按"水位"
     * （最后一次已投影到迷雾的时间戳）把新增点增量 reveal 一次即可。
     * 迷雾单调只升，本操作幂等，重复执行不会出错。
     */
    private suspend fun catchUpFogFromBackgroundTracks() {
        val nowMs = clock.now().toEpochMilli()
        val watermark = settings.fogWatermarkMs()
        if (watermark != null) {
            val points = trackRepository.between(watermark + 1, nowMs)
            if (points.isNotEmpty()) {
                points.forEach { point ->
                    explorationState = revealFog(explorationState, point.coordinate)
                }
                explorationRepository.saveStates(explorationState.cellStates)
            }
        }
        // 首次运行（水位为空）不做补算：此刻已持久化的迷雾本来就是对的。
        // 水位推到 now，避免每次回前台都重扫一大段历史点。
        settings.setFogWatermarkMs(nowMs)
    }

    fun setShowTrack(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showTrack = visible)
        mapRenderer.setLayerVisible(MapLayer.TRACK, visible)
    }

    fun setShowFog(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showFog = visible)
        mapRenderer.setLayerVisible(MapLayer.FOG_MASK, visible)
    }

    // ---- 图层筛选（逻辑隐藏；持久化到 DataStore） ----

    fun toggleFilterPanel() {
        _uiState.value = _uiState.value.copy(showFilterPanel = !_uiState.value.showFilterPanel)
    }

    fun togglePlaceType(type: PlaceType) {
        val current = _filters.value.shownPlaceTypes
        val next = if (type in current) current - type else current + type
        persistFilter(_filters.value.copy(shownPlaceTypes = next))
    }

    fun toggleMemories() {
        persistFilter(_filters.value.copy(showMemories = !_filters.value.showMemories))
    }

    fun setTimeFilter(filter: MemoryTimeFilter) {
        persistFilter(_filters.value.copy(memoryTimeFilter = filter))
    }

    private fun persistFilter(filter: MapFilterSettings) {
        _filters.value = filter
        viewModelScope.launch { settings.setMapFilter(filter) }
    }

    fun onObserveClicked() {
        val place = _uiState.value.selectedPlace ?: return
        val coordinate = lastCoordinate ?: return
        viewModelScope.launch {
            when (val result = observePlace(coordinate, place)) {
                is ObserveResult.Success ->
                    showToast("获得「${result.resourceName}」×${result.amount}（共 ${result.newQuantity}）")
                is ObserveResult.Rejected -> showToast(
                    when (result.reason) {
                        ObserveRejectReason.TOO_FAR -> "离地点太远了"
                        ObserveRejectReason.ON_COOLDOWN -> "刚观察过，让它安静一会儿"
                        ObserveRejectReason.ACTION_NOT_AVAILABLE -> "这里没什么可观察的"
                        ObserveRejectReason.NOTHING_HERE -> "这时候看不出什么，换个天气或时段再来"
                        ObserveRejectReason.REWARD_FAILED -> "观察失败了"
                    },
                )
            }
        }
    }

    /** 点地图地点图标：仅已揭示且类型被显示的地点才可选。 */
    fun onPlaceTapped(placeId: String) {
        viewModelScope.launch {
            placeRepository.placeById(placeId)?.let { place ->
                if (isRevealed(place) && place.type in _filters.value.shownPlaceTypes) {
                    selectPlace(place)
                }
            }
        }
    }

    /** 未探索地点（格子没见过）：true = 灰色 "?" 状态。 */
    private fun isRevealed(place: Place): Boolean =
        explorationState.stateOf(gridManager.grid.cellOf(place.coordinate)).rank >=
            CellFogState.DISCOVERED.rank

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

    /** 关闭聚焦卡：清掉高亮环并消费请求（避免返回地图时又跳一次）。 */
    fun clearMemoryFocus() {
        _uiState.value = _uiState.value.copy(focusedMemory = null)
        mapRenderer.renderFocus(null)
        memoryFocus.consume()
    }

    private fun focusMemory(memory: MemoryNode) {
        _uiState.value = _uiState.value.copy(
            focusedMemory = memory,
            selectedPlace = null,
        )
        mapRenderer.renderFocus(memory.coordinate)
        mapRenderer.setCamera(MapCamera(memory.coordinate, FOCUS_ZOOM))
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
        refreshTrack()
        refreshPlaces(coordinate)

        _uiState.value = _uiState.value.copy(
            revealedCount = explorationState.revealedCount(),
            lastFix = coordinate,
        )
    }

    /** 关闭轨迹日历的聚焦卡：回到"今天"的轨迹，并消费请求。 */
    fun clearTrackDayFocus() {
        _uiState.value = _uiState.value.copy(focusedTrackDay = null)
        trackDayFocus.consume()
        viewModelScope.launch { refreshTrack() }
    }

    /**
     * 轨迹日历选中某天：画那天的轨迹，并把相机移到这段轨迹的范围。
     * 没点的日子（理论上日历不会给）就只清空。
     */
    private suspend fun focusTrackDay(date: LocalDate) {
        val points = trackRepository.between(dayStartEpochMs(date), dayEndEpochMs(date))
        _uiState.value = _uiState.value.copy(
            focusedTrackDay = FocusedTrackDay(
                date = date,
                pointCount = points.size,
                lengthMeters = trackLengthMeters(points),
                startLabel = points.firstOrNull()?.let(::timeLabelOf) ?: "--:--",
                endLabel = points.lastOrNull()?.let(::timeLabelOf) ?: "--:--",
            ),
            selectedPlace = null,
        )
        mapRenderer.renderTrack(points.map { it.coordinate })
        trackCamera(points)?.let(mapRenderer::setCamera)
    }

    private fun timeLabelOf(point: TrackPoint): String =
        MEMORY_TIME_FORMAT.format(Instant.ofEpochMilli(point.timestampEpochMs).atZone(TRACK_ZONE))

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

    /** 画轨迹：选了某天就画那天，否则画今天。 */
    private suspend fun refreshTrack() {
        val focused = _uiState.value.focusedTrackDay
        if (focused == null) {
            refreshTodayTrack()
            return
        }
        val points = trackRepository
            .between(dayStartEpochMs(focused.date), dayEndEpochMs(focused.date))
            .map { it.coordinate }
        mapRenderer.renderTrack(points)
    }

    private suspend fun refreshPlaces(coordinate: WorldCoordinate) {
        val all = placeRepository.nearby(coordinate, PLACE_MARKER_RADIUS_METERS)
        val (revealed, unrevealed) = all.partition { isRevealed(it) }
        val shownRevealed = revealed.filter { it.type in _filters.value.shownPlaceTypes }
        // 已揭示 + 类型被显示 → 彩色图标+名字；未探索 → 灰色 "?"（不受类型筛选影响）。
        mapRenderer.renderPlaces(
            shownRevealed.map { PlaceVisual(it.id, it.name, it.coordinate, it.type, revealed = true) } +
                unrevealed.map { PlaceVisual(it.id, "", it.coordinate, it.type, revealed = false) },
        )
        _uiState.value = _uiState.value.copy(
            nearbyPlaces = shownRevealed.filter {
                it.coordinate.distanceMetersTo(coordinate) <= PLACE_CARD_RADIUS_METERS
            },
        )
    }

    /** 记忆标记：按筛选（是否显示 + 时间窗）决定画哪些；只带一个时间小字。 */
    private fun renderMemoriesNow() {
        if (!_filters.value.showMemories) {
            mapRenderer.renderMemories(emptyList())
            return
        }
        val nowMs = clock.now().toEpochMilli()
        val timeFilter = _filters.value.memoryTimeFilter
        viewModelScope.launch {
            val shown = memoryRepository.latest(MEMORY_LIMIT)
                .filter { inTimeWindow(it.createdAtEpochMs, timeFilter, nowMs) }
            mapRenderer.renderMemories(
                shown.map {
                    MemoryVisual(
                        coordinate = it.coordinate,
                        mood = it.mood,
                        timeLabel = MEMORY_TIME_FORMAT.format(
                            java.time.Instant.ofEpochMilli(it.createdAtEpochMs).atZone(TRACK_ZONE),
                        ),
                    )
                },
            )
        }
    }

    private fun inTimeWindow(tsMs: Long, filter: MemoryTimeFilter, nowMs: Long): Boolean =
        when (filter) {
            MemoryTimeFilter.ALL -> true
            MemoryTimeFilter.TODAY -> {
                val dayStart = clock.now().atZone(TRACK_ZONE).toLocalDate()
                    .atStartOfDay(TRACK_ZONE).toInstant().toEpochMilli()
                tsMs in dayStart..nowMs
            }
            MemoryTimeFilter.THIS_WEEK -> tsMs in (nowMs - WEEK_MS)..nowMs
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
        /** 「在地图查看」聚焦时的缩放：比默认更近，看清目标周边。 */
        const val FOCUS_ZOOM = 18.0
        const val PLACE_MARKER_RADIUS_METERS = 600.0
        const val PLACE_CARD_RADIUS_METERS = 150.0
        private const val FOG_EXPAND_FACTOR = 1.35
        private const val MEMORY_LIMIT = 200
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        private val MEMORY_TIME_FORMAT: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}
