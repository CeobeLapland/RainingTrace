package com.rainingtrace.domain.track

import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.WorldCoordinate
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDayTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)

    private fun point(
        metersNorth: Double,
        ts: Long,
        id: String = "p$ts",
    ) = TrackPoint(
        id = id,
        timestampEpochMs = ts,
        coordinate = WorldCoordinate(
            origin.latDegrees + metersNorth / 111_320.0,
            origin.lngDegrees,
        ),
        accuracyMeters = 10.0,
        source = LocationSource.GPS,
    )

    @Test
    fun `local day index buckets around midnight in shanghai`() {
        // 2026-09-19 00:30 +08:00 == 2026-09-18T16:30Z
        val afterMidnight = LocalDate.of(2026, 9, 19)
            .atTime(0, 30)
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli()
        val beforeMidnight = LocalDate.of(2026, 9, 18)
            .atTime(23, 59)
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli()

        assertEquals(LocalDate.of(2026, 9, 19).toEpochDay(), localDayIndex(afterMidnight))
        assertEquals(LocalDate.of(2026, 9, 18).toEpochDay(), localDayIndex(beforeMidnight))
    }

    @Test
    fun `day start and end cover exactly one local day`() {
        val date = LocalDate.of(2026, 9, 19)
        assertEquals(DAY_MS, dayEndEpochMs(date) - dayStartEpochMs(date))
        // 起点落在本地零点（等于 UTC 前一天 16:00）
        assertEquals(
            LocalDate.of(2026, 9, 19).atStartOfDay(TRACK_ZONE).toInstant().toEpochMilli(),
            dayStartEpochMs(date),
        )
    }

    @Test
    fun `track length sums consecutive segments`() {
        val points = listOf(point(0.0, 1L), point(100.0, 2L), point(250.0, 3L))
        assertEquals(250.0, trackLengthMeters(points), 0.5)
    }

    @Test
    fun `track length of single point is zero`() {
        assertEquals(0.0, trackLengthMeters(listOf(point(0.0, 1L))), 0.001)
    }

    @Test
    fun `camera centers on the track and zooms out for longer spans`() {
        val short = listOf(point(0.0, 1L), point(120.0, 2L))
        val long = listOf(point(0.0, 1L), point(2000.0, 2L))

        val shortCamera = trackCamera(short)
        val longCamera = trackCamera(long)

        assertNotNull(shortCamera)
        assertNotNull(longCamera)
        // 更长的一段 → 更小的缩放（看得更远）
        assertTrue(longCamera!!.zoom < shortCamera!!.zoom)
    }

    @Test
    fun `camera zoom stays inside usable range for a single point`() {
        val camera: MapCamera? = trackCamera(listOf(point(0.0, 1L)))
        assertNotNull(camera)
        assertEquals(18.5, camera!!.zoom, 0.001)
    }

    @Test
    fun `camera of empty track is null`() {
        assertNull(trackCamera(emptyList()))
    }

    @Test
    fun `track day maps epoch day index back to local date`() {
        val day = trackDayOf(
            dayIndex = LocalDate.of(2026, 9, 19).toEpochDay(),
            pointCount = 12,
            firstEpochMs = 1L,
            lastEpochMs = 2L,
        )
        assertEquals(LocalDate.of(2026, 9, 19), day.date)
        assertEquals(12, day.pointCount)
    }
}