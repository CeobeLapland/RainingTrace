package com.rainingtrace.domain.world

import com.rainingtrace.core.time.WORLD_ZONE
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldStateTest {

    /** 本地时间 → Instant（+08:00）。 */
    private fun instantAt(hour: Int, minute: Int = 0, day: Int = 19) =
        LocalDate.of(2026, 9, day)
            .atTime(hour, minute)
            .toInstant(ZoneOffset.ofHours(8))

    @Test
    fun `time of day buckets follow the documented boundaries`() {
        assertEquals(TimeOfDay.NIGHT, timeOfDayOf(0))
        assertEquals(TimeOfDay.NIGHT, timeOfDayOf(4 * 60 + 59))
        assertEquals(TimeOfDay.DAWN, timeOfDayOf(5 * 60))
        assertEquals(TimeOfDay.DAWN, timeOfDayOf(7 * 60 + 59))
        assertEquals(TimeOfDay.DAY, timeOfDayOf(8 * 60))
        assertEquals(TimeOfDay.DAY, timeOfDayOf(16 * 60 + 59))
        assertEquals(TimeOfDay.DUSK, timeOfDayOf(17 * 60))
        assertEquals(TimeOfDay.DUSK, timeOfDayOf(19 * 60 + 59))
        assertEquals(TimeOfDay.NIGHT, timeOfDayOf(20 * 60))
        assertEquals(TimeOfDay.NIGHT, timeOfDayOf(23 * 60 + 59))
    }

    @Test
    fun `derive uses local zone for date and minute of day`() {
        val state = deriveWorldState(
            instant = instantAt(hour = 6, minute = 30),
            weather = WeatherState(WeatherKind.FOG),
        )

        assertEquals(LocalDate.of(2026, 9, 19), state.localDate)
        assertEquals(6 * 60 + 30, state.minuteOfDay)
        assertEquals(TimeOfDay.DAWN, state.timeOfDay)
        assertEquals(WeatherKind.FOG, state.weather.kind)
        assertEquals(WORLD_ZONE, state.instant.atZone(WORLD_ZONE).zone)
    }

    @Test
    fun `utc instants near midnight land on the correct local day`() {
        // 2026-09-19 00:30 +08:00 == 2026-09-18T16:30Z
        val state = deriveWorldState(
            instant = instantAt(hour = 0, minute = 30),
            weather = WeatherState(WeatherKind.CLEAR),
        )
        assertEquals(LocalDate.of(2026, 9, 19), state.localDate)
        assertEquals(TimeOfDay.NIGHT, state.timeOfDay)
    }

    @Test
    fun `season and holiday are unset until rules exist`() {
        val state = deriveWorldState(
            instant = instantAt(hour = 12),
            weather = WeatherState(WeatherKind.CLEAR),
        )
        assertNull(state.season)
        assertNull(state.holiday)
    }

    @Test
    fun `season passes through when supplied`() {
        val state = deriveWorldState(
            instant = instantAt(hour = 12),
            weather = WeatherState(WeatherKind.CLEAR),
            season = Season.AUTUMN,
            holiday = "holiday.mid_autumn",
        )
        assertEquals(Season.AUTUMN, state.season)
        assertEquals("holiday.mid_autumn", state.holiday)
    }

    @Test
    fun `rainy kinds are derived from the enum flag`() {
        assertTrue(WeatherKind.RAINY.contains(WeatherKind.LIGHT_RAIN))
        assertTrue(WeatherKind.RAINY.contains(WeatherKind.HEAVY_RAIN))
        assertFalse(WeatherKind.RAINY.contains(WeatherKind.SNOW))
        assertTrue(WeatherState(WeatherKind.HEAVY_RAIN).isRaining)
        assertFalse(WeatherState(WeatherKind.CLOUDY).isRaining)
    }

    @Test
    fun `fake weather presets are internally consistent`() {
        // 不能出现雪天 20 度这种矛盾组合
        assertTrue(weatherPreset(WeatherKind.SNOW).temperatureCelsius < 0)
        assertTrue(weatherPreset(WeatherKind.HEAVY_RAIN).humidity > 0.9)
        assertTrue(weatherPreset(WeatherKind.CLEAR).humidity < 0.5)
    }

    @Test
    fun `fake world state provider serves the snapshot it was given`() {
        val state = deriveWorldState(
            instant = instantAt(hour = 21),
            weather = WeatherState(WeatherKind.LIGHT_RAIN),
        )
        val provider = FakeWorldStateProvider(state)

        assertEquals(state, provider.current())
        assertEquals(TimeOfDay.NIGHT, provider.state.value.timeOfDay)
    }
}