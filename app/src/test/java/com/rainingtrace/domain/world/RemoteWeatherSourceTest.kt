package com.rainingtrace.domain.world

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.RawLocationFix
import com.rainingtrace.domain.map.WorldCoordinate
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 注意：轮询是**无限循环 + delay**，所以这里一律用 `runCurrent()` /
 * `advanceTimeBy(确定时长)`，**绝不能用 `advanceUntilIdle()`**——那会一直推进到无任务为止，
 * 在这个循环上永远等不到尽头。
 */
class RemoteWeatherSourceTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)
    private val clock = FakeWorldClock(Instant.parse("2026-09-24T08:00:00Z"))

    private class FakeLocation(private val coordinate: WorldCoordinate?) : LocationProvider {
        override val updates: Flow<RawLocationFix> = flowOf()
        override val latest: RawLocationFix? get() = coordinate?.let {
            RawLocationFix(it, accuracyMeters = 5.0, timestampEpochMs = 0L, source = LocationSource.GPS)
        }
    }

    private class RecordingApi(var result: WeatherState?) : WeatherApi {
        var calls = 0
        override suspend fun currentWeather(coordinate: WorldCoordinate): WeatherState? {
            calls++
            return result
        }
    }

    private fun TestScope.buildSource(
        api: WeatherApi,
        coordinate: WorldCoordinate? = origin,
        foreground: StateFlow<Boolean> = MutableStateFlow(true),
        initial: WeatherState? = null,
    ) = RemoteWeatherSource(
        api = api,
        locationProvider = FakeLocation(coordinate),
        isForeground = foreground,
        clock = clock,
        scope = backgroundScope,
        initial = initial,
    )

    /** 订出收集者把轮询冷流养起来，然后跑到首次拉取结束。 */
    private fun TestScope.startCollecting(source: WeatherSource) {
        backgroundScope.launch { source.weather.collect {} }
        runCurrent()
    }

    @Test
    fun `fetches on subscribe and exposes the weather`() = runTest {
        val api = RecordingApi(WeatherState(WeatherKind.LIGHT_RAIN, humidity = 0.8, temperatureCelsius = 16.0))
        val source = buildSource(api)
        startCollecting(source)

        assertEquals(1, api.calls)
        assertEquals(WeatherKind.LIGHT_RAIN, source.weather.value.kind)
        assertEquals(0.8, source.weather.value.humidity, 0.0001)
        assertNotNull("成功后要记下更新时间", source.status.value.lastUpdatedAtEpochMs)
        assertFalse(source.status.value.isRetrying)
    }

    @Test
    fun `manual override wins and suppresses fetching`() = runTest {
        val api = RecordingApi(WeatherState(WeatherKind.SNOW))
        val source = buildSource(api)
        startCollecting(source)
        val callsBefore = api.calls

        source.setOverride(weatherPreset(WeatherKind.CLEAR))
        runCurrent()

        assertEquals(WeatherKind.CLEAR, source.weather.value.kind)
        assertEquals(WeatherKind.CLEAR, source.manualOverride.value?.kind)

        advanceTimeBy(REFRESH_MS + SLACK_MS)
        runCurrent()
        assertEquals("覆盖生效时不该再拉 API", callsBefore, api.calls)
        assertEquals("覆盖期间值不变", WeatherKind.CLEAR, source.weather.value.kind)
    }

    @Test
    fun `clearing the override returns to the real value`() = runTest {
        val api = RecordingApi(WeatherState(WeatherKind.FOG))
        val source = buildSource(api)
        startCollecting(source)

        source.setOverride(weatherPreset(WeatherKind.CLEAR))
        runCurrent()
        assertEquals(WeatherKind.CLEAR, source.weather.value.kind)

        source.setOverride(null)
        runCurrent()
        assertEquals("清掉覆盖立刻回到最近一次真实值", WeatherKind.FOG, source.weather.value.kind)
    }

    @Test
    fun `no location fix means no fetch and no false retrying`() = runTest {
        val api = RecordingApi(WeatherState(WeatherKind.CLEAR))
        val source = buildSource(api, coordinate = null)
        startCollecting(source)

        assertEquals("不知道问哪儿，就不该发请求", 0, api.calls)
        assertFalse("没定位不算失败", source.status.value.isRetrying)
        // 占位值是"多云"而不是"晴"——"不知道"不能说成晴。
        assertEquals(WeatherKind.CLOUDY, source.weather.value.kind)
    }

    @Test
    fun `background pauses fetching without flagging retrying`() = runTest {
        val api = RecordingApi(WeatherState(WeatherKind.CLEAR))
        val foreground = MutableStateFlow(true)
        val source = buildSource(api, foreground = foreground)
        startCollecting(source)
        val callsBefore = api.calls

        foreground.value = false
        advanceTimeBy(REFRESH_MS + SLACK_MS)
        runCurrent()

        assertEquals("后台不该拉", callsBefore, api.calls)
        assertFalse("暂停不是失败", source.status.value.isRetrying)
    }

    @Test
    fun `failure keeps last good value and flags retrying`() = runTest {
        val api = RecordingApi(WeatherState(WeatherKind.LIGHT_RAIN))
        val source = buildSource(api)
        startCollecting(source)
        assertEquals(WeatherKind.LIGHT_RAIN, source.weather.value.kind)

        api.result = null // 开始失败
        advanceTimeBy(REFRESH_MS + SLACK_MS)
        runCurrent()

        assertEquals("失败后保留上一次好值", WeatherKind.LIGHT_RAIN, source.weather.value.kind)
        assertTrue("要标记成重试中", source.status.value.isRetrying)
    }

    @Test
    fun `recovery clears retrying`() = runTest {
        val api = RecordingApi(WeatherState(WeatherKind.FOG))
        val source = buildSource(api)
        startCollecting(source)

        api.result = null
        advanceTimeBy(REFRESH_MS + SLACK_MS)
        runCurrent()
        assertTrue(source.status.value.isRetrying)

        api.result = WeatherState(WeatherKind.SNOW)
        advanceTimeBy(RETRY_MS + SLACK_MS) // 失败后的重试间隔是 1 分钟
        runCurrent()

        assertFalse(source.status.value.isRetrying)
        assertEquals(WeatherKind.SNOW, source.weather.value.kind)
    }

    @Test
    fun `cached initial value is used before the first successful fetch`() = runTest {
        val cached = WeatherState(WeatherKind.CLOUDY, humidity = 0.7, temperatureCelsius = 18.0)
        val source = buildSource(RecordingApi(null), initial = cached)

        // 还没订出去：StateFlow 直接给 initialValue，也就是缓存值。
        assertEquals(WeatherKind.CLOUDY, source.weather.value.kind)
        assertEquals(clock.now().toEpochMilli(), source.status.value.lastUpdatedAtEpochMs)
        assertFalse(source.status.value.isRetrying)
    }

    @Test
    fun `successful fetch is written to the cache`() = runTest {
        val cache = RecordingCache()
        val api = RecordingApi(WeatherState(WeatherKind.SNOW))
        val source = RemoteWeatherSource(
            api = api,
            locationProvider = FakeLocation(origin),
            isForeground = MutableStateFlow(true),
            clock = clock,
            scope = backgroundScope,
            cache = cache,
        )
        startCollecting(source)

        assertEquals(1, cache.saved.size)
        assertEquals(WeatherKind.SNOW, cache.saved.single().state.kind)
        assertEquals(clock.now().toEpochMilli(), cache.saved.single().fetchedAtEpochMs)
    }

    @Test
    fun `failed fetch writes nothing to the cache`() = runTest {
        val cache = RecordingCache()
        val source = RemoteWeatherSource(
            api = RecordingApi(null),
            locationProvider = FakeLocation(origin),
            isForeground = MutableStateFlow(true),
            clock = clock,
            scope = backgroundScope,
            cache = cache,
        )
        startCollecting(source)

        assertTrue("失败不该污染缓存", cache.saved.isEmpty())
        assertNull(cache.load())
    }

    @Test
    fun `paused fetch writes nothing to the cache`() = runTest {
        val cache = RecordingCache()
        val source = RemoteWeatherSource(
            api = RecordingApi(WeatherState(WeatherKind.SNOW)),
            locationProvider = FakeLocation(origin),
            isForeground = MutableStateFlow(true),
            clock = clock,
            scope = backgroundScope,
            cache = cache,
        )
        source.setOverride(weatherPreset(WeatherKind.CLEAR))
        startCollecting(source)

        assertTrue("手动覆盖时不拉取，也就没有东西可缓存", cache.saved.isEmpty())
    }

    private class RecordingCache : WeatherCache {
        val saved = mutableListOf<WeatherCacheEntry>()
        override suspend fun load(): WeatherCacheEntry? = saved.lastOrNull()
        override suspend fun save(entry: WeatherCacheEntry) {
            saved += entry
        }
    }

    private companion object {
        // RemoteWeatherSource 的这两个常量是私有的，这里按同样的量级取值。
        const val REFRESH_MS = 15 * 60 * 1000L
        const val RETRY_MS = 60 * 1000L
        const val SLACK_MS = 1_000L
    }
}