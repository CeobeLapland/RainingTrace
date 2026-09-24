package com.rainingtrace.domain.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationWatchdogTest {

    private fun action(silent: Long, probeDone: Boolean = false, reRequestDone: Boolean = false) =
        locationWatchdogAction(silent, probeDone, reRequestDone)

    @Test
    fun `stays quiet before the probe threshold`() {
        assertEquals(LocationWatchdogAction.WAIT, action(0))
        assertEquals(LocationWatchdogAction.WAIT, action(LOCATION_PROBE_AFTER_MS - 1))
    }

    @Test
    fun `probes once the first threshold is crossed`() {
        assertEquals(
            LocationWatchdogAction.PROBE_LAST_KNOWN,
            action(LOCATION_PROBE_AFTER_MS),
        )
    }

    @Test
    fun `waits between probe and re-request when the probe was already done`() {
        assertEquals(
            LocationWatchdogAction.WAIT,
            action(LOCATION_RE_REQUEST_AFTER_MS - 1, probeDone = true),
        )
    }

    @Test
    fun `re-requests after the second threshold`() {
        assertEquals(
            LocationWatchdogAction.RE_REQUEST,
            action(LOCATION_RE_REQUEST_AFTER_MS, probeDone = true),
        )
    }

    /** 优先级：还没探过就先探，别跳过便宜的那一步直接重发。 */
    @Test
    fun `probe wins over re-request when both are pending`() {
        assertEquals(
            LocationWatchdogAction.PROBE_LAST_KNOWN,
            action(LOCATION_WARN_AFTER_MS, probeDone = false, reRequestDone = false),
        )
    }

    @Test
    fun `waits between re-request and warn`() {
        assertEquals(
            LocationWatchdogAction.WAIT,
            action(LOCATION_WARN_AFTER_MS - 1, probeDone = true, reRequestDone = true),
        )
    }

    @Test
    fun `warns only after both remedies were tried`() {
        assertEquals(
            LocationWatchdogAction.WARN,
            action(LOCATION_WARN_AFTER_MS, probeDone = true, reRequestDone = true),
        )
    }

    /**
     * 这条是给接线处看的：裸调 [locationWatchdogAction] 时，只要标志位没置上，
     * 同一个静默区间里的每次 tick 都会重复返回 PROBE——所以必须走 [LocationWatchdogState]。
     */
    @Test
    fun `bare decision repeats itself if the caller forgets to set the flags`() {
        assertEquals(LocationWatchdogAction.PROBE_LAST_KNOWN, action(100_000))
        assertEquals(LocationWatchdogAction.PROBE_LAST_KNOWN, action(100_000))
    }

    @Test
    fun `state hands out each remedy only once per silent period`() {
        val state = LocationWatchdogState()

        // 静默期一开始：什么也不做
        assertEquals(LocationWatchdogAction.WAIT, state.consume(1_000))
        assertFalse(state.probeDone)

        // 到点：先探一次，而且只探一次
        assertEquals(LocationWatchdogAction.PROBE_LAST_KNOWN, state.consume(100_000))
        assertTrue(state.probeDone)
        assertEquals(LocationWatchdogAction.WAIT, state.consume(110_000))

        // 还没到重发点：继续等
        assertEquals(LocationWatchdogAction.WAIT, state.consume(150_000))

        // 到点：重发一次，也只重发一次
        assertEquals(LocationWatchdogAction.RE_REQUEST, state.consume(200_000))
        assertTrue(state.reRequestDone)
        assertEquals(LocationWatchdogAction.WAIT, state.consume(210_000))

        // 两条补救都用过了才开始报警
        assertEquals(LocationWatchdogAction.WARN, state.consume(400_000))
    }

    @Test
    fun `reset starts a fresh silent period`() {
        val state = LocationWatchdogState()
        state.consume(100_000)
        state.consume(200_000)
        assertTrue(state.probeDone)
        assertTrue(state.reRequestDone)

        state.reset()

        assertFalse(state.probeDone)
        assertFalse(state.reRequestDone)
        // 新一轮里应该又能补救一次
        assertEquals(LocationWatchdogAction.PROBE_LAST_KNOWN, state.consume(100_000))
    }
}