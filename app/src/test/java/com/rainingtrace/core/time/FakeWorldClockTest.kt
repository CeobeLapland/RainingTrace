package com.rainingtrace.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FakeWorldClockTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `returns fixed instant`() {
        val clock = FakeWorldClock(Instant.parse("2026-09-13T04:00:00Z"))
        assertEquals(Instant.parse("2026-09-13T04:00:00Z"), clock.now())
        assertEquals(Instant.parse("2026-09-13T04:00:00Z"), clock.now())
    }

    @Test
    fun `advance moves time forward`() {
        val clock = FakeWorldClock(Instant.parse("2026-09-13T04:00:00Z"))
        clock.advanceSeconds(3600)
        assertEquals(Instant.parse("2026-09-13T05:00:00Z"), clock.now())
    }

    @Test
    fun `local date uses provided zone`() {
        // 2026-09-12T17:00Z = 北京时间 2026-09-13 01:00
        val clock = FakeWorldClock(Instant.parse("2026-09-12T17:00:00Z"))
        assertEquals("2026-09-13", clock.localDate(zone).toString())
    }

    @Test
    fun `set replaces instant`() {
        val clock = FakeWorldClock(Instant.EPOCH)
        clock.set(Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), clock.now())
    }
}
