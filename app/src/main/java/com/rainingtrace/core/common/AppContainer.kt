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
import com.rainingtrace.domain.inventory.InventoryRepository
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
import com.rainingtrace.platform.ar.ArCoreController
import com.rainingtrace.platform.camera.CameraXController
import com.rainingtrace.platform.location.FakeLocationProvider
import com.rainingtrace.platform.map.MapLibreAdapter
import kotlinx.coroutines.runBlocking

/**
 * RT-BOOT-003: 手写 DI（AppContainer）。
 *
 * 决策：MVP 使用 AppContainer + 构造注入，不引入 Hilt
 * （人工确认 2026-09-13；符合"40 行优于 100 行"原则）。
 *
 * 依赖方向保持 feature → domain ← platform/data。
 */
class AppContainer(context: Context) {

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

    /** 供仍需直接取网格的只读便捷属性（实时跟随档位）。 */
    val grid: HexGrid get() = gridManager.grid

    /**
     * 是否使用 Fake 定位。UI 据此显示「点击地图 = 移动」调试提示。
     * RT-BOOT-006 完成后与定位模式设置打通（S4）。
     */
    val useFakeLocation: Boolean = true

    private val fakeLocationProvider: FakeLocationProvider by lazy {
        FakeLocationProvider(clock = clock, initial = worldOrigin)
    }

    val locationProvider: LocationProvider by lazy {
        fakeLocationProvider
    }

    /** 调试用：Fake 模式下点击地图 = 移动；真实定位模式下为 null（S4 切换）。 */
    val debugMapTap: ((WorldCoordinate) -> Unit)? = { coordinate ->
        fakeLocationProvider.emit(coordinate)
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
