package com.rainingtrace.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.core.lifecycle.AppForegroundState
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.exploration.PerformPlaceActionUseCase
import com.rainingtrace.domain.exploration.PlaceActionRejectReason
import com.rainingtrace.domain.exploration.PlaceActionResult
import com.rainingtrace.domain.exploration.PlaceYieldPreview
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
import com.rainingtrace.domain.map.NpcVisual
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceCategory
import com.rainingtrace.domain.map.PlaceDraft
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.PlaceVisual
import com.rainingtrace.domain.map.PlaceWriteResult
import com.rainingtrace.domain.map.PlaceWriter
import com.rainingtrace.domain.map.placeVisualsFor
import com.rainingtrace.domain.map.PlayerMarkerVisual
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.memory.MemoryFocusRequest
import com.rainingtrace.domain.memory.MemoryNode
import com.rainingtrace.domain.memory.MemoryRepository
import com.rainingtrace.domain.npc.NpcEncounterResult
import com.rainingtrace.domain.npc.NpcPresence
import com.rainingtrace.domain.npc.NpcPresenceUseCase
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcRepository
import com.rainingtrace.domain.npc.RecordNpcEncounterUseCase
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
import com.rainingtrace.domain.track.isStandingStill
import com.rainingtrace.domain.track.trackCamera
import com.rainingtrace.domain.track.trackLengthMeters
import com.rainingtrace.domain.world.WorldStateProvider
import kotlinx.coroutines.delay
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
    /** 已选中的 NPC → 只读卡片（与 [selectedPlace] 互斥）。 */
    val selectedNpc: NpcPresence? = null,
    /** 选中 NPC 的档案（名字/一句话）；[selectedNpc] 不为空时才有值。 */
    val selectedNpcProfile: NpcProfile? = null,
    /** 日记「在地图查看」跳过来的记忆 → 聚焦卡 + 高亮环。 */
    val focusedMemory: MemoryNode? = null,
    /** 轨迹日历跳过来的某一天 → 底部摘要卡 + 画那天的轨迹（空 = 画今天）。 */
    val focusedTrackDay: FocusedTrackDay? = null,
    /** 观察范围内的已揭示地点（按距离升序），驱动"附近多地点"列表。 */
    val nearbyPlaces: List<Place> = emptyList(),
    /** 选中地点各动作的"此刻产出"提示；选中时才计算，清空选择即丢弃。 */
    val actionPreviews: Map<PlaceActionType, PlaceYieldPreview> = emptyMap(),
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
    private val performPlaceAction: PerformPlaceActionUseCase,
    private val placeRepository: PlaceRepository,
    private val explorationRepository: ExplorationRepository,
    private val memoryRepository: MemoryRepository,
    /** 日记「在地图查看」的一次性聚焦请求。 */
    private val memoryFocus: MemoryFocusRequest,
    /** 轨迹日历「在地图查看」的一次性聚焦请求。 */
    private val trackDayFocus: TrackDayFocusRequest,
    /** NPC：按作息算出此刻在哪（纯函数，不落库）。 */
    private val npcPresence: NpcPresenceUseCase,
    private val npcRepository: NpcRepository,
    /** 走到 NPC 跟前时记一次"第一次遇见"。 */
    private val recordNpcEncounter: RecordNpcEncounterUseCase,
    /** NPC 位置与世界状态（时段）同源：都用它现算，避免读到过期 tick。 */
    private val worldState: WorldStateProvider,
    /** 前后台状态：后台不跑迷雾/渲染/查地点（省电边界）。 */
    private val foregroundState: AppForegroundState,
    /** Fake 模式下点击地图移动；GPS 模式内部忽略。 */
    private val debugMapTap: ((WorldCoordinate) -> Unit)?,
    /** 本地偏好：定位模式 + 图层筛选（筛选作为唯一真相，重启保留）。 */
    private val settings: AppSettingsRepository,
    /** 权限授予后重新拉起 GPS 采集。 */
    private val refreshLocation: () -> Unit,
    /** 现场采点：把当前位置记成一个地点（写进内容覆盖层）。 */
    private val placeWriter: PlaceWriter,
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
            launch { runNpcTicker() }
        }
    }

    /**
     * NPC 的刷新循环：位置是时间的纯函数，所以要"看到人在走"只能周期性重渲。
     *
     * 三重门控（顺序不能变，否则会破坏省电口径）：
     * 1. 前台——不可见时不查、不画；
     * 2. 有人在走**且**在视口内——都停着就什么都不做；
     * 3. 只写 rt-npcs 一个 source（见 [MapRendererAdapter.renderNpcs] 的约定）。
     *
     * 10s 一步：校园相邻地点 150~300m / 10~20 分钟路程 ≈ 每步挪 2~5m，
     * 视觉上是"在挪动"而不是瞬移。
     */
    private suspend fun runNpcTicker() {
        while (true) {
            delay(NPC_TICK_MS)
            if (!foregroundState.isForeground.value) continue
            val viewport = this.viewport ?: continue
            val presences = npcPresence.presencesAt(worldState.current())
            if (presences.none { it.walking && viewport.contains(it.coordinate) }) continue
            mapRenderer.renderNpcs(presences.map(::toNpcVisual))
            syncSelectedNpc(presences)
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
        // 拖到别处后 NPC 不该等到下一个 tick 才出现。
        viewModelScope.launch { renderNpcsNow() }
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
            renderNpcsNow()
            mapRenderer.setLayerVisible(MapLayer.TRACK, _uiState.value.showTrack)
            mapRenderer.setLayerVisible(MapLayer.FOG_MASK, _uiState.value.showFog)
            // MapView 重建后高亮环也没了，补一次。
            _uiState.value.focusedMemory?.let { mapRenderer.renderFocus(it.coordinate) }
            // 回到前台/切回本屏时重算产出提示：可能刚在设置里改过天气或季节。
            refreshActionPreviews()
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

    /** 点地点卡上的动作按钮：观察 / 采集走同一个入口，只是 action 不同。 */
    fun onPlaceAction(action: PlaceActionType) {
        val place = _uiState.value.selectedPlace ?: return
        val coordinate = lastCoordinate ?: return
        viewModelScope.launch {
            when (val result = performPlaceAction(coordinate, place, action)) {
                is PlaceActionResult.Success ->
                    showToast("获得「${result.resourceName}」×${result.amount}（共 ${result.newQuantity}）")
                is PlaceActionResult.Rejected -> showToast(
                    when (result.reason) {
                        PlaceActionRejectReason.TOO_FAR ->
                            if (action == PlaceActionType.COLLECT) "再走近一点才能采" else "离地点太远了"
                        PlaceActionRejectReason.ON_COOLDOWN -> "刚来过，让它安静一会儿"
                        PlaceActionRejectReason.ACTION_NOT_AVAILABLE -> "这里不能这么做"
                        PlaceActionRejectReason.NOTHING_HERE -> "这时候看不出什么，换个天气或时段再来"
                        PlaceActionRejectReason.REWARD_FAILED -> "这次没成功"
                    },
                )
            }
            // 结果会改变冷却状态，提示要跟着更新（从"有产出"变成"刚来过"）。
            refreshActionPreviews()
        }
    }

    /** 重算选中地点的各动作产出提示；没有选中地点就清空。 */
    private suspend fun refreshActionPreviews() {
        val place = _uiState.value.selectedPlace ?: return
        val previews = place.actions.associateWith { action ->
            performPlaceAction.preview(place, action)
        }
        // 计算期间选择可能已变（异步），只在仍是同一地点时落地。
        if (_uiState.value.selectedPlace?.id == place.id) {
            _uiState.value = _uiState.value.copy(actionPreviews = previews)
        }
    }

    /**
     * 现场采点：把当前位置记成一个地点。
     *
     * 成功后**立刻选中它**——新点若所在格还没被揭示，`placeVisualsFor` 不会画它，
     * 详情卡就是唯一的即时确认。
     */
    fun captureCurrentPlace(draft: PlaceDraft) {
        viewModelScope.launch {
            when (val result = placeWriter.addPlace(draft)) {
                is PlaceWriteResult.Added -> {
                    showToast("已经记下「${draft.name.trim()}」")
                    lastCoordinate?.let { refreshPlaces(it) }
                    placeRepository.placeById(result.id)?.let { selectPlace(it) }
                }

                is PlaceWriteResult.Rejected -> showToast("没记下来：${result.reason}")
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
        if (_uiState.value.selectedPlace?.id == place.id) {
            clearSelection()
            return
        }
        // 先清空旧提示，避免显示上一个地点的产出；地点卡与 NPC 卡互斥。
        _uiState.value = _uiState.value.copy(
            selectedPlace = place,
            selectedNpc = null,
            selectedNpcProfile = null,
            actionPreviews = emptyMap(),
        )
        viewModelScope.launch { refreshActionPreviews() }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPlace = null, actionPreviews = emptyMap())
    }

    /** 点地图上的 NPC 图标：只读卡片，显示此刻在哪、在做什么。 */
    fun onNpcTapped(npcId: String) {
        if (_uiState.value.selectedNpc?.npcId == npcId) {
            clearNpcSelection()
            return
        }
        viewModelScope.launch {
            val presence = npcPresence.presenceOf(npcId, worldState.current()) ?: return@launch
            _uiState.value = _uiState.value.copy(
                selectedNpc = presence,
                selectedNpcProfile = npcRepository.byId(npcId),
                selectedPlace = null,
                actionPreviews = emptyMap(),
            )
        }
    }

    fun clearNpcSelection() {
        _uiState.value = _uiState.value.copy(selectedNpc = null, selectedNpcProfile = null)
    }

    /** 立即按当前时刻重算并渲染 NPC（切回本屏 / 视口变化时用）。 */
    private suspend fun renderNpcsNow() {
        val presences = npcPresence.presencesAt(worldState.current())
        mapRenderer.renderNpcs(presences.map(::toNpcVisual))
        syncSelectedNpc(presences)
    }

    /** 选中的 NPC 可能刚换了地点或开始走路，卡片内容要跟着刷新。 */
    private fun syncSelectedNpc(presences: List<NpcPresence>) {
        val selected = _uiState.value.selectedNpc ?: return
        val updated = presences.firstOrNull { it.npcId == selected.npcId } ?: return
        if (updated != selected) {
            _uiState.value = _uiState.value.copy(selectedNpc = updated)
        }
    }

    private fun toNpcVisual(presence: NpcPresence) = NpcVisual(
        npcId = presence.npcId,
        npcName = presence.npcName,
        coordinate = presence.coordinate,
        walking = presence.walking,
    )

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
            selectedNpc = null,
            selectedNpcProfile = null,
            actionPreviews = emptyMap(),
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
        if (result !is RecordTrackResult.Accepted) {
            // 上一条轨迹点就在脚下（没走出 8m）时不会产生新点。若冷启动正好如此，
            // 地图会整片空白——玩家标记、地点、附近卡片一个都不出现，看着像世界没了。
            // 这里只补一次渲染：不落库、不重算迷雾，漂移保护（精度差 / 瞬移）不受影响。
            if (lastCoordinate == null && (result as RecordTrackResult.Rejected).reason.isStandingStill()) {
                mapRenderer.renderPlayer(PlayerMarkerVisual(fix.coordinate))
                refreshPlaces(fix.coordinate)
            }
            return
        }

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
        recordNpcEncounters(coordinate)
    }

    /**
     * 走到 NPC 跟前就记一次"第一次遇见"（只记第一次）。
     *
     * 放在定位回包里而不是 NPC ticker 里：这里已有稳定坐标，而且不受"有没有人在走"影响。
     * [RecordNpcEncounterUseCase] 先判距离再查库，所以常态下没有额外查询。
     */
    private suspend fun recordNpcEncounters(playerCoordinate: WorldCoordinate) {
        val presences = npcPresence.presencesAt(worldState.current())
        presences.forEach { presence ->
            val result = recordNpcEncounter(playerCoordinate, presence)
            if (result is NpcEncounterResult.Met) {
                showToast("第一次遇见「${result.npcName}」")
            }
        }
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
            selectedNpc = null,
            selectedNpcProfile = null,
            actionPreviews = emptyMap(),
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
        // 画什么由 domain 决定（已揭示/筛选/资源点未揭示不画），见 placeVisualsFor。
        mapRenderer.renderPlaces(
            placeVisualsFor(
                places = all,
                isRevealed = ::isRevealed,
                shownTypes = _filters.value.shownPlaceTypes,
            ),
        )
        // 附近列表只列人文地点：自然资源点会有很多，塞进来会把地点淹没。
        // 资源点靠地图图标点选（走进去就看见了）。
        _uiState.value = _uiState.value.copy(
            nearbyPlaces = all.filter { place ->
                place.type.category == PlaceCategory.PLACE &&
                    place.type in _filters.value.shownPlaceTypes &&
                    isRevealed(place) &&
                    place.coordinate.distanceMetersTo(coordinate) <= PLACE_CARD_RADIUS_METERS
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

        /** NPC 重渲间隔：见 [runNpcTicker] 的取舍（10s 一步刚好"在挪动"）。 */
        private const val NPC_TICK_MS = 10_000L
        private val MEMORY_TIME_FORMAT: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}
