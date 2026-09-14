package com.rainingtrace.domain.track

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.RawLocationFix
import com.rainingtrace.domain.map.WorldCoordinate
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordTrackPointUseCaseTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)
    private val clock = FakeWorldClock(Instant.parse("2026-09-13T12:00:00Z"))

    private class FakeTrackRepository : TrackRepository {
        val points = mutableListOf<TrackPoint>()
        override suspend fun append(point: TrackPoint) {
            points.add(point)
        }
        override suspend fun latestPoint(): TrackPoint? =
            points.maxByOrNull { it.timestampEpochMs }
        override suspend fun between(fromEpochMs: Long, toEpochMs: Long): List<TrackPoint> =
            points.filter { it.timestampEpochMs in fromEpochMs..toEpochMs }
        override suspend fun all(): List<TrackPoint> = points
    }

    private fun fix(
        coordinate: WorldCoordinate,
        accuracyMeters: Double = 10.0,
        timestampEpochMs: Long = clock.now().toEpochMilli(),
        source: LocationSource = LocationSource.FAKE,
    ) = RawLocationFix(
        coordinate = coordinate,
        accuracyMeters = accuracyMeters,
        timestampEpochMs = timestampEpochMs,
        source = source,
    )

    /** 沿经线向北偏移 [metersNorth] 米（1° 纬度约 111320m）。 */
    private fun northOf(metersNorth: Double): WorldCoordinate =
        WorldCoordinate(origin.latDegrees + metersNorth / 111_320.0, origin.lngDegrees)

    @Test
    fun `first fix is always accepted`() = runTest {
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)

        val result = useCase(fix(origin))

        assertTrue(result is RecordTrackResult.Accepted)
        assertEquals(1, repo.points.size)
        assertEquals(LocationSource.FAKE, (result as RecordTrackResult.Accepted).point.source)
    }

    @Test
    fun `poor accuracy rejected`() = runTest {
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)

        useCase(fix(origin))
        clock.advanceSeconds(10)
        val result = useCase(
            fix(northOf(30.0), accuracyMeters = 80.0, source = LocationSource.GPS),
        )

        assertEquals(RejectReason.POOR_ACCURACY, (result as RecordTrackResult.Rejected).reason)
        assertEquals(1, repo.points.size)
    }

    @Test
    fun `stale fix rejected`() = runTest {
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)
        useCase(fix(origin))

        clock.advanceSeconds(60)
        val stale = fix(
            northOf(30.0),
            timestampEpochMs = clock.now().toEpochMilli() - 31_000L,
        )
        val result = useCase(stale)

        assertEquals(RejectReason.STALE, (result as RecordTrackResult.Rejected).reason)
    }

    @Test
    fun `future timestamp rejected`() = runTest {
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)

        val future = fix(
            origin,
            timestampEpochMs = clock.now().toEpochMilli() + 30_000L,
        )
        val result = useCase(future)

        assertEquals(RejectReason.FUTURE_TIMESTAMP, (result as RecordTrackResult.Rejected).reason)
        assertEquals(0, repo.points.size)
    }

    @Test
    fun `tiny movement rejected as too close`() = runTest {
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)
        useCase(fix(origin))

        clock.advanceSeconds(5)
        val result = useCase(fix(northOf(5.0))) // < 8m 去抖阈值

        assertEquals(RejectReason.TOO_CLOSE, (result as RecordTrackResult.Rejected).reason)
        assertEquals(1, repo.points.size)
    }

    @Test
    fun `implausible jump rejected`() = runTest {
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)
        useCase(fix(origin))

        clock.advanceSeconds(1)
        val result = useCase(fix(northOf(100.0), source = LocationSource.GPS)) // 100m/s

        assertEquals(RejectReason.IMPLAUSIBLE_JUMP, (result as RecordTrackResult.Rejected).reason)
        assertEquals(1, repo.points.size)
    }

    @Test
    fun `steady walking fix accepted`() = runTest {
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)
        useCase(fix(origin))

        clock.advanceSeconds(10)
        val result = useCase(fix(northOf(30.0))) // 3m/s，正常步行

        assertTrue(result is RecordTrackResult.Accepted)
        assertEquals(2, repo.points.size)
    }

    @Test
    fun `implausible jump accepted for fake source`() = runTest {
        // Fake 点是显式调试输入：允许"点地图瞬移"，不做速度拦截
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)
        useCase(fix(origin))

        clock.advanceSeconds(1)
        val result = useCase(fix(northOf(100.0), source = LocationSource.FAKE))

        assertTrue(result is RecordTrackResult.Accepted)
        assertEquals(2, repo.points.size)
    }

    @Test
    fun `poor accuracy accepted for fake source but rejected for gps`() = runTest {
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)
        useCase(fix(origin))
        clock.advanceSeconds(10)

        val fakeBad = useCase(fix(northOf(30.0), accuracyMeters = 80.0, source = LocationSource.FAKE))
        assertTrue(fakeBad is RecordTrackResult.Accepted)

        clock.advanceSeconds(10)
        val gpsBad = useCase(fix(northOf(60.0), accuracyMeters = 80.0, source = LocationSource.GPS))
        assertEquals(RejectReason.POOR_ACCURACY, (gpsBad as RecordTrackResult.Rejected).reason)
    }

    @Test
    fun `non advancing timestamp rejected`() = runTest {
        val repo = FakeTrackRepository()
        val useCase = RecordTrackPointUseCase(repo, clock)
        useCase(fix(origin))

        val result = useCase(fix(northOf(30.0))) // 时间戳与上点相同

        assertEquals(
            RejectReason.NOT_MOVING_FORWARD,
            (result as RecordTrackResult.Rejected).reason,
        )
    }
}
