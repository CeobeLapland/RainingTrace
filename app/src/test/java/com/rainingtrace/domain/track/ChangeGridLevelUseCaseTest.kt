package com.rainingtrace.domain.track

import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.map.GridLevel
import com.rainingtrace.domain.map.GridManager
import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.settings.MapFilterSettings
import com.rainingtrace.domain.settings.NpcClockOffset
import com.rainingtrace.domain.settings.NpcMessageSettings
import com.rainingtrace.domain.settings.TrackingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeGridLevelUseCaseTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)

    private class FakeSettings : AppSettingsRepository {
        var level: GridLevel = GridLevel.M
        var mode: LocationMode = LocationMode.FAKE
        var trackingSettings = TrackingSettings()
        var watermarkMs: Long? = null

        override val gridLevel: Flow<GridLevel> = flowOf(level)
        override suspend fun currentGridLevel() = level
        override suspend fun setGridLevel(level: GridLevel) {
            this.level = level
        }
        override val locationMode: Flow<LocationMode> = flowOf(mode)
        override suspend fun currentLocationMode() = mode
        override suspend fun setLocationMode(mode: LocationMode) {
            this.mode = mode
        }
        override val mapFilter: Flow<MapFilterSettings> = flowOf(MapFilterSettings())
        override suspend fun currentMapFilter() = MapFilterSettings()
        override suspend fun setMapFilter(filter: MapFilterSettings) = Unit
        override val tracking: Flow<TrackingSettings> = flowOf(trackingSettings)
        override suspend fun currentTracking() = trackingSettings
        override suspend fun setTracking(settings: TrackingSettings) {
            trackingSettings = settings
        }
        override suspend fun fogWatermarkMs() = watermarkMs
        override suspend fun setFogWatermarkMs(epochMs: Long) {
            watermarkMs = epochMs
        }
        override val npcClockOffset: Flow<NpcClockOffset> = flowOf(NpcClockOffset.DEFAULT)
        override suspend fun currentNpcClockOffset() = NpcClockOffset.DEFAULT
        override suspend fun setNpcClockOffset(offset: NpcClockOffset) = Unit
        override val npcMessages: Flow<NpcMessageSettings> = flowOf(NpcMessageSettings())
        override suspend fun currentNpcMessages() = NpcMessageSettings()
        override suspend fun setNpcMessages(settings: NpcMessageSettings) = Unit
    }

    private class FakeExploration : ExplorationRepository {
        var state = ExplorationState()
        var cleared = false
        override fun observeState(): Flow<ExplorationState> = flowOf(state)
        override suspend fun loadState() = state
        override suspend fun saveStates(states: Map<HexCellId, CellFogState>) {
            state = ExplorationState(states)
        }
        override suspend fun clearLevel() {
            cleared = true
            state = ExplorationState()
        }
    }

    private class FakeTracks(points: List<TrackPoint>) : TrackRepository {
        val all = points
        override suspend fun append(point: TrackPoint) {}
        override suspend fun latestPoint() = all.maxByOrNull { it.timestampEpochMs }
        override suspend fun between(fromEpochMs: Long, toEpochMs: Long) = all
        override suspend fun days(zoneOffsetMs: Long): List<TrackDay> = emptyList()
        override suspend fun all() = all
    }

    private fun point(northMeters: Double, ts: Long) = TrackPoint(
        id = "p$ts",
        timestampEpochMs = ts,
        coordinate = WorldCoordinate(origin.latDegrees + northMeters / 111_320.0, origin.lngDegrees),
        accuracyMeters = 10.0,
        source = LocationSource.FAKE,
    )

    @Test
    fun `changing level clears and rebuilds fog from track points on new grid`() = runTest {
        val settings = FakeSettings()
        val manager = GridManager(GridLevel.M, origin)
        val exploration = FakeExploration()
        val tracks = FakeTracks(listOf(point(0.0, 1L), point(45.0, 2L), point(120.0, 3L)))
        // 先在 40m 档算出基线格数
        val rebuild40 = RebuildFogFromTrackUseCase(RevealFogFromPointUseCase(manager))
        val state40 = rebuild40(tracks.all())

        val rebuild = RebuildFogFromTrackUseCase(RevealFogFromPointUseCase(manager))
        val change = ChangeGridLevelUseCase(manager, settings, exploration, tracks, rebuild)

        change(GridLevel.S) // 25m 格

        assertEquals(GridLevel.S, manager.level)
        assertEquals(GridLevel.S, settings.level)
        // 新网格比 40m 细：同样轨迹覆盖的格数应当更多
        assert(exploration.state.revealedCount() > state40.revealedCount()) {
            "finer grid should reveal more cells: ${exploration.state.revealedCount()} vs ${state40.revealedCount()}"
        }
        // 原点格必为 VISITED
        assertEquals(
            CellFogState.VISITED,
            exploration.state.stateOf(manager.grid.cellOf(origin)),
        )
    }

    @Test
    fun `selecting same level is a no op`() = runTest {
        val settings = FakeSettings()
        val manager = GridManager(GridLevel.M, origin)
        val exploration = FakeExploration()
        val tracks = FakeTracks(emptyList())
        val rebuild = RebuildFogFromTrackUseCase(RevealFogFromPointUseCase(manager))
        val change = ChangeGridLevelUseCase(manager, settings, exploration, tracks, rebuild)

        change(GridLevel.M)
        assertEquals(false, exploration.cleared)
    }
}
