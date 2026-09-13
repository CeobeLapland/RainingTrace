package com.rainingtrace.core.common

import android.content.Context
import com.rainingtrace.core.time.SystemWorldClock
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.data.repository.FakePlaceRepository
import com.rainingtrace.domain.exploration.MarkCellVisitedUseCase
import com.rainingtrace.domain.exploration.RevealNearbyCellsUseCase
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
 * MVP 阶段全部 Fake/内存实现；真实 SDK 接入时在此替换，
 * 依赖方向保持 feature → domain ← platform/data。
 */
class AppContainer(context: Context) {

    // 世界原点：北湖（参考坐标，真机试玩后校准）
    private val worldOrigin = WorldCoordinate(39.7326, 116.1712)

    val clock: WorldClock by lazy { SystemWorldClock() }

    val grid: HexGrid by lazy { HexGrid(origin = worldOrigin, cellSizeMeters = 80.0) }

    val locationProvider: LocationProvider by lazy {
        FakeLocationProvider(clock = clock, initial = worldOrigin)
    }

    val mapRenderer: MapRendererAdapter by lazy { MapLibreAdapter() }

    val placeRepository: PlaceRepository by lazy { FakePlaceRepository() }

    val revealNearbyCells: RevealNearbyCellsUseCase by lazy { RevealNearbyCellsUseCase(grid) }

    val markCellVisited: MarkCellVisitedUseCase by lazy { MarkCellVisitedUseCase() }
}
