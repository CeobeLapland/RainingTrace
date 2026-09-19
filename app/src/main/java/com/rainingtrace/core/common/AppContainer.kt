package com.rainingtrace.core.common

import android.content.Context
import com.rainingtrace.core.lifecycle.AppForegroundState
import com.rainingtrace.core.time.SystemWorldClock
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.data.local.RainingTraceDatabase
import com.rainingtrace.data.repository.FakePlaceRepository
import com.rainingtrace.data.repository.RoomExplorationRepository
import com.rainingtrace.data.repository.RoomFootprintRepository
import com.rainingtrace.data.repository.RoomInventoryRepository
import com.rainingtrace.data.repository.RoomMemoryRepository
import com.rainingtrace.data.repository.RoomTrackRepository
import com.rainingtrace.data.settings.DataStoreSettingsRepository
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.ObservePlaceUseCase
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.domain.inventory.InMemoryResourceCatalog
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.ResourceCatalog
import com.rainingtrace.domain.map.GridManager
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.ar.ArController
import com.rainingtrace.domain.memory.AudioNoteController
import com.rainingtrace.domain.memory.CreateMemoryUseCase
import com.rainingtrace.domain.memory.MemoryFocusRequest
import com.rainingtrace.domain.memory.MemoryRepository
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.track.ChangeGridLevelUseCase
import com.rainingtrace.domain.track.RebuildFogFromTrackUseCase
import com.rainingtrace.domain.track.RecordTrackPointUseCase
import com.rainingtrace.domain.track.RevealFogFromPointUseCase
import com.rainingtrace.domain.track.TrackDayFocusRequest
import com.rainingtrace.domain.track.TrackRepository
import com.rainingtrace.domain.track.TrackingController
import com.rainingtrace.domain.world.FakeWeatherProvider
import com.rainingtrace.domain.world.InMemoryResourceYieldRuleCatalog
import com.rainingtrace.domain.world.MutableWeatherProvider
import com.rainingtrace.domain.world.ResourceYieldRuleCatalog
import com.rainingtrace.domain.world.SystemWorldStateProvider
import com.rainingtrace.domain.world.WeatherProvider
import com.rainingtrace.domain.world.WorldStateProvider
import com.rainingtrace.platform.ar.ArCoreController
import com.rainingtrace.platform.audio.AndroidAudioNoteController
import com.rainingtrace.platform.camera.CameraXController
import com.rainingtrace.platform.location.AndroidTrackingController
import com.rainingtrace.platform.location.FakeLocationProvider
import com.rainingtrace.platform.map.MapLibreAdapter
import com.rainingtrace.platform.location.AndroidLocationProvider
import com.rainingtrace.platform.location.SwitchableLocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * RT-BOOT-003: 手写 DI（AppContainer）。
 *
 * 决策：MVP 使用 AppContainer + 构造注入，不引入 Hilt
 * （人工确认 2026-09-13；符合"40 行优于 100 行"原则）。
 *
 * 依赖方向保持 feature → domain ← platform/data。
 */
class AppContainer(
    context: Context,
    private val applicationScope: CoroutineScope,
) {

    private val appContext: Context = context.applicationContext

    // 世界原点：北湖（参考坐标，真机试玩后校准）
    private val worldOrigin = WorldCoordinate(39.7326, 116.1712)

    private val database: RainingTraceDatabase by lazy {
        RainingTraceDatabase.create(context)
    }

    val clock: WorldClock by lazy { SystemWorldClock() }

    val settingsRepository: AppSettingsRepository by lazy {
        DataStoreSettingsRepository(appContext)
    }

    /** 格子档位与当前网格；启动时从偏好恢复。 */
    val gridManager: GridManager = GridManager(
        initialLevel = runBlocking { settingsRepository.currentGridLevel() },
        origin = worldOrigin,
    )

    /** 启动时恢复的定位模式（Fake 默认，点击地图调试）。 */
    val initialLocationMode: com.rainingtrace.domain.settings.LocationMode =
        runBlocking { settingsRepository.locationMode.first() }

    /** 供仍需直接取网格的只读便捷属性（实时跟随档位）。 */
    val grid: HexGrid get() = gridManager.grid

    private val fakeLocationProvider: FakeLocationProvider by lazy {
        FakeLocationProvider(clock = clock, initial = worldOrigin)
    }

    private val androidLocationProvider: AndroidLocationProvider by lazy {
        AndroidLocationProvider(appContext, clock)
    }

    val locationProvider: SwitchableLocationProvider by lazy {
        SwitchableLocationProvider(
            fake = fakeLocationProvider,
            android = androidLocationProvider,
            initialMode = initialLocationMode,
            parentScope = applicationScope,
            modeFlow = settingsRepository.locationMode,
        )
    }

    /** 供 feature 判断当前定位模式（权限流、Fake 提示等）。 */
    val locationModeFlow get() = settingsRepository.locationMode

    /** 应用前后台状态：地图处理流与后台记录服务的唯一省电闸门。 */
    val foregroundState = AppForegroundState()

    val trackingController: TrackingController by lazy {
        AndroidTrackingController(appContext)
    }

    /** 定位权限是否已授予（监督者与记录服务共用同一判断）。 */
    fun hasLocationPermission(): Boolean = androidLocationProvider.hasPermission()

    /**
     * 足迹记录服务的唯一决策点：开关开 + GPS 模式 + 有定位权限 = 该记录。
     *
     * 停随时可以停；启只能发生在应用可见时——Android 14 起禁止从后台启动
     * location 类型前台服务。所以这里也把前台状态作为一个输入。
     */
    private fun syncTrackingService(enabled: Boolean, mode: LocationMode, foreground: Boolean) {
        val shouldRun = enabled && mode == LocationMode.GPS && hasLocationPermission()
        when {
            shouldRun && foreground -> trackingController.start()
            !shouldRun -> trackingController.stop()
            // shouldRun 但当前不可见：不做任何事。服务本该已在可见时拉起；
            // 若被系统杀掉，则等下次回到前台再启动（这是唯一合法的启动时机）。
        }
    }

    init {
        applicationScope.launch {
            combine(
                settingsRepository.tracking,
                settingsRepository.locationMode,
                foregroundState.isForeground,
            ) { tracking, mode, foreground -> Triple(tracking.enabled, mode, foreground) }
                .distinctUntilChanged()
                .collect { (enabled, mode, foreground) ->
                    syncTrackingService(enabled, mode, foreground)
                }
        }
    }

    /** 定位权限状态变化后重新尝试拉起 GPS 采集（并重新评估足迹记录服务）。 */
    fun refreshLocation() {
        locationProvider.refresh()
        applicationScope.launch {
            syncTrackingService(
                enabled = settingsRepository.currentTracking().enabled,
                mode = settingsRepository.currentLocationMode(),
                foreground = foregroundState.isForeground.value,
            )
        }
    }

    /** 调试用：仅 Fake 模式点击地图有效。 */
    val debugMapTap: (WorldCoordinate) -> Unit = { coordinate ->
        locationProvider.emitDebugMove(coordinate)
    }

    val mapRenderer: MapRendererAdapter by lazy { MapLibreAdapter() }

    val placeRepository: PlaceRepository by lazy { FakePlaceRepository() }

    val explorationRepository: ExplorationRepository by lazy {
        RoomExplorationRepository(database.explorationDao(), gridManager)
    }

    val trackRepository: TrackRepository by lazy {
        RoomTrackRepository(database.trackPointDao())
    }

    val footprintRepository: FootprintRepository by lazy {
        RoomFootprintRepository(database.footprintDao())
    }

    val inventoryRepository: InventoryRepository by lazy {
        RoomInventoryRepository(database.inventoryDao())
    }

    /** 资源/图鉴目录：定义库存在这里，库存量只在 inventoryRepository。 */
    val resourceCatalog: ResourceCatalog by lazy {
        InMemoryResourceCatalog(InMemoryResourceCatalog.DEFAULT)
    }

    /** 世界天气状态：MVP 用 Fake（固定晴）；P1 换真实 API 适配器，条件与产出不用改。 */
    val weatherProvider: WeatherProvider by lazy { FakeWeatherProvider() }

    /** 供设置页的调试区手动改天气（真实 API 版不实现 MutableWeatherProvider，界面自动隐藏）。 */
    val mutableWeatherProvider: MutableWeatherProvider? get() = weatherProvider as? MutableWeatherProvider

    /**
     * 世界状态（时间 + 天气 + 季节/节日）：资源产出、事件、NPC 出现的统一输入口。
     * 只在有人订阅时推进（WhileSubscribed），后台不空转。
     */
    val worldStateProvider: WorldStateProvider by lazy {
        SystemWorldStateProvider(
            clock = clock,
            weatherProvider = weatherProvider,
            scope = applicationScope,
        )
    }

    /** 产出规则表：加内容只加规则，不改用例。 */
    val resourceYieldRules: ResourceYieldRuleCatalog by lazy {
        InMemoryResourceYieldRuleCatalog(InMemoryResourceYieldRuleCatalog.DEFAULT)
    }

    val addItem: AddItemToInventoryUseCase by lazy { AddItemToInventoryUseCase(clock) }

    val recordTrackPoint: RecordTrackPointUseCase by lazy {
        RecordTrackPointUseCase(trackRepository, clock)
    }

    val revealFog: RevealFogFromPointUseCase by lazy { RevealFogFromPointUseCase(gridManager) }

    val rebuildFog: RebuildFogFromTrackUseCase by lazy {
        RebuildFogFromTrackUseCase(revealFog)
    }

    val changeGridLevel: ChangeGridLevelUseCase by lazy {
        ChangeGridLevelUseCase(
            gridManager = gridManager,
            settings = settingsRepository,
            explorationRepository = explorationRepository,
            trackRepository = trackRepository,
            rebuild = rebuildFog,
        )
    }

    val observePlace: ObservePlaceUseCase by lazy {
        ObservePlaceUseCase(
            clock = clock,
            inventoryRepository = inventoryRepository,
            addItem = addItem,
            footprintRepository = footprintRepository,
            resourceCatalog = resourceCatalog,
            worldState = worldStateProvider,
            rules = resourceYieldRules,
        )
    }

    val memoryRepository: MemoryRepository by lazy {
        RoomMemoryRepository(database.memoryDao())
    }

    /** 日记 →「在地图查看」：一次性聚焦请求，地图侧消费后清空。 */
    val memoryFocusRequest: MemoryFocusRequest = MemoryFocusRequest()

    /** 轨迹日历 →「在地图查看」：同上，聚焦某一天的轨迹。 */
    val trackDayFocusRequest: TrackDayFocusRequest = TrackDayFocusRequest()

    val cameraController: CameraXController by lazy {
        CameraXController(context = appContext, clock = clock)
    }

    /** 语音记录/回放：录音失败必须能降级为纯文字记忆。 */
    val audioNoteController: AudioNoteController by lazy {
        AndroidAudioNoteController(context = appContext, clock = clock)
    }

    val createMemory: CreateMemoryUseCase by lazy {
        CreateMemoryUseCase(
            gridManager = gridManager,
            clock = clock,
            memoryRepository = memoryRepository,
            explorationRepository = explorationRepository,
            footprintRepository = footprintRepository,
            worldState = worldStateProvider,
        )
    }

    val arController: ArController by lazy {
        ArCoreController(
            context = appContext,
            locationProvider = locationProvider,
            clock = clock,
        )
    }
}
