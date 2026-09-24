package com.rainingtrace.domain.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WmoWeatherCodeTest {

    @Test
    fun `clear and cloudy codes`() {
        assertEquals(WeatherKind.CLEAR, weatherKindOfWmoCode(0))
        assertEquals(WeatherKind.CLEAR, weatherKindOfWmoCode(1))
        assertEquals(WeatherKind.CLOUDY, weatherKindOfWmoCode(2))
        assertEquals(WeatherKind.CLOUDY, weatherKindOfWmoCode(3))
    }

    @Test
    fun `fog codes`() {
        assertEquals(WeatherKind.FOG, weatherKindOfWmoCode(45))
        assertEquals(WeatherKind.FOG, weatherKindOfWmoCode(48))
    }

    @Test
    fun `drizzle and light rain codes`() {
        listOf(51, 53, 55, 56, 57, 61, 63, 80, 81).forEach { code ->
            assertEquals("code $code", WeatherKind.LIGHT_RAIN, weatherKindOfWmoCode(code))
        }
    }

    @Test
    fun `heavy rain and thunderstorm codes`() {
        listOf(65, 66, 67, 82, 95, 96, 99).forEach { code ->
            assertEquals("code $code", WeatherKind.HEAVY_RAIN, weatherKindOfWmoCode(code))
        }
    }

    @Test
    fun `snow codes`() {
        listOf(71, 73, 75, 77, 85, 86).forEach { code ->
            assertEquals("code $code", WeatherKind.SNOW, weatherKindOfWmoCode(code))
        }
    }

    /** 未知码必须是 null：让调用方保留上次好值，而不是把"不知道"说成晴。 */
    @Test
    fun `unknown code is null`() {
        assertNull(weatherKindOfWmoCode(4))
        assertNull(weatherKindOfWmoCode(70))
        assertNull(weatherKindOfWmoCode(100))
        assertNull(weatherKindOfWmoCode(-1))
    }

    /**
     * WMO 4677 里"风"是独立变量（wind_speed_10m），不是天气码，
     * 所以这张表**永远**不会给出 [WeatherKind.WIND]——不是漏了。
     */
    @Test
    fun `wind is never produced by a weather code`() {
        (0..120).forEach { code ->
            assert(weatherKindOfWmoCode(code) != WeatherKind.WIND) {
                "code $code 不该映射成 WIND"
            }
        }
    }
}