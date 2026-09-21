package com.rainingtrace.domain.world

import com.rainingtrace.core.time.FakeWorldClock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DerivedSeasonSourceTest {

    private fun clockAt(year: Int, month: Int, day: Int) = FakeWorldClock(
        LocalDate.of(year, month, day).atTime(12, 0).toInstant(ZoneOffset.ofHours(8)),
    )

    /** stateIn 只在有人订阅时推进，所以测试里要保持一个收集者。 */
    private fun CoroutineScope.subscribe(source: DerivedSeasonSource) {
        launch { source.season.collect {} }
    }

    @Test
    fun `derives the season from the solar term when there is no override`() = runTest {
        val source = DerivedSeasonSource(clockAt(2026, 9, 21), backgroundScope)
        backgroundScope.subscribe(source)
        runCurrent()

        assertEquals(Season.AUTUMN, source.season.value)
        assertNull(source.manualOverride.value)
    }

    @Test
    fun `manual override wins over the derived season`() = runTest {
        val source = DerivedSeasonSource(clockAt(2026, 9, 21), backgroundScope)
        backgroundScope.subscribe(source)
        runCurrent()

        source.setSeason(Season.SPRING)
        runCurrent()

        assertEquals(Season.SPRING, source.season.value)
        assertEquals(Season.SPRING, source.manualOverride.value)
    }

    @Test
    fun `clearing the override goes back to the derived season`() = runTest {
        val source = DerivedSeasonSource(clockAt(2026, 9, 21), backgroundScope)
        backgroundScope.subscribe(source)
        runCurrent()

        source.setSeason(Season.WINTER)
        runCurrent()
        assertEquals(Season.WINTER, source.season.value)

        source.setSeason(null)
        runCurrent()
        assertEquals(Season.AUTUMN, source.season.value)
        assertNull(source.manualOverride.value)
    }

    @Test
    fun `derived season follows the clock across a solar term boundary`() = runTest {
        // 立冬 2026-11-07：前一天还是秋，当天变冬
        val clock = clockAt(2026, 11, 6)
        val source = DerivedSeasonSource(clock, backgroundScope)
        backgroundScope.subscribe(source)
        runCurrent()
        assertEquals(Season.AUTUMN, source.season.value)

        clock.set(LocalDate.of(2026, 11, 7).atTime(12, 0).toInstant(ZoneOffset.ofHours(8)))
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(Season.WINTER, source.season.value)
    }
}
