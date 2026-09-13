package com.rainingtrace.core.common

import android.content.Context
import com.rainingtrace.core.time.SystemWorldClock
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.data.local.RainingTraceDatabase
import com.rainingtrace.data.repository.FakePlaceRepository
import com.rainingtrace.data.repository.RoomExplorationRepository
import com.rainingtrace.data.repository.RoomFootprintRepository
import com.rainingtrace.data.repository.RoomInventoryRepository
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.MarkCellVisitedUseCase
import com.rainingtrace.domain.exploration.ObservePlaceUseCase
import com.rainingtrace.domain.exploration.RevealNearbyCellsUseCase
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.platform.location.FakeLocationProvider
import com.rainingtrace.platform.map.MapLibreAdapter

/**
 * RT-BOOT-003: 手写 DI（AppContainer）。
 *
 * 决策：MVP 使用 AppContainer + 构造注入，不引入 Hilt
 * （人工确认 2026-09-13；符合"40 行优于 100 行"原则）。
 *
 * 依赖方向保持 feature → domain ← platform/data。
 */
class AppContainer(context: Context) {

    // 世界原点：北湖（参考坐标，真机试玩后校准）
    private val worldOrigin = WorldCoordinate(39.7326, 116.1712)

    private val database: RainingTraceDatabase by lazy {
        RainingTraceDatabase.create(context)
    }

    val clock: WorldClock by lazy { SystemWorldClock() }

    val grid: HexGrid by lazy { HexGrid(origin = worldOrigin, cellSizeMeters = 80.0) }

    val locationProvider: LocationProvider by lazy {
        FakeLocationProvider(clock = clock, initial = worldOrigin)
    }

    val mapRenderer: MapRendererAdapter by lazy { MapLibreAdapter() }

    val placeRepository: PlaceRepository by lazy { FakePlaceRepository() }

    val explorationRepository: ExplorationRepository by lazy {
        RoomExplorationRepository(database.explorationDao())
    }

    val footprintRepository: FootprintRepository by lazy {
        RoomFootprintRepository(database.footprintDao())
    }

    val inventoryRepository: InventoryRepository by lazy {
        RoomInventoryRepository(database.inventoryDao())
    }

    val addItem: AddItemToInventoryUseCase by lazy { AddItemToInventoryUseCase(clock) }

    val revealNearbyCells: RevealNearbyCellsUseCase by lazy { RevealNearbyCellsUseCase(grid) }

    val markCellVisited: MarkCellVisitedUseCase by lazy { MarkCellVisitedUseCase() }

    val observePlace: ObservePlaceUseCase by lazy {
        ObservePlaceUseCase(
            grid = grid,
            clock = clock,
            inventoryRepository = inventoryRepository,
            addItem = addItem,
            footprintRepository = footprintRepository,
        )
    }
}
