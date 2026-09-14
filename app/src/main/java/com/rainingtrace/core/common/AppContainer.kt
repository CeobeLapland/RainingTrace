package com.rainingtrace.core.common

import android.content.Context
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
import com.rainingtrace.domain.memory.CreateMemoryUseCase
import com.rainingtrace.domain.memory.MemoryRepository
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.track.ChangeGridLevelUseCase
import com.rainingtrace.domain.track.RebuildFogFromTrackUseCase
import com.rainingtrace.domain.track.RecordTrackPointUseCase
import com.rainingtrace.domain.track.RevealFogFromPointUseCase
import com.rainingtrace.domain.track.TrackRepository
import com.rainingtrace.domain.world.FakeWeatherProvider
import com.rainingtrace.domain.world.WeatherProvider
import com.rainingtrace.platform.ar.ArCoreController
import com.rainingtrace.platform.camera.CameraXController
import com.rainingtrace.platform.location.FakeLocationProvider
import com.rainingtrace.platform.map.MapLibreAdapter
import com.rainingtrace.platform.location.AndroidLocationProvider
import com.rainingtrace.platform.location.SwitchableLocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
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

    /** 定位权限状态变化后重新尝试拉起 GPS 采集。 */
    fun refreshLocation() = locationProvider.refresh()

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

    /** 世界天气状态：MVP 用 Fake（固定晴），P1 接真实 API。 */
    val weatherProvider: WeatherProvider by lazy { FakeWeatherProvider() }

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
        )
    }

    val memoryRepository: MemoryRepository by lazy {
        RoomMemoryRepository(database.memoryDao())
    }

    val cameraController: CameraXController by lazy {
        CameraXController(context = appContext, clock = clock)
    }

    val createMemory: CreateMemoryUseCase by lazy {
        CreateMemoryUseCase(
            gridManager = gridManager,
            clock = clock,
            memoryRepository = memoryRepository,
            explorationRepository = explorationRepository,
            footprintRepository = footprintRepository,
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
