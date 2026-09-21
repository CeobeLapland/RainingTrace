package com.rainingtrace.domain.world

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarTermSeasonTest {

    @Test
    fun `spring starts on lichun and the day before is still winter`() {
        val lichun = solarTermDate(2026, SolarTerm.LICHUN)
        assertEquals(LocalDate.of(2026, 2, 4), lichun)
        assertEquals(Season.SPRING, seasonOf(lichun))
        assertEquals(Season.WINTER, seasonOf(lichun.minusDays(1)))
    }

    @Test
    fun `each term flips the season on its own day`() {
        assertEquals(Season.SPRING, seasonOf(solarTermDate(2026, SolarTerm.LICHUN)))
        assertEquals(Season.SUMMER, seasonOf(solarTermDate(2026, SolarTerm.LIXIA)))
        assertEquals(Season.AUTUMN, seasonOf(solarTermDate(2026, SolarTerm.LIQIU)))
        assertEquals(Season.WINTER, seasonOf(solarTermDate(2026, SolarTerm.LIDONG)))
    }

    @Test
    fun `the day before each term still belongs to the previous season`() {
        assertEquals(Season.WINTER, seasonOf(solarTermDate(2026, SolarTerm.LICHUN).minusDays(1)))
        assertEquals(Season.SPRING, seasonOf(solarTermDate(2026, SolarTerm.LIXIA).minusDays(1)))
        assertEquals(Season.SUMMER, seasonOf(solarTermDate(2026, SolarTerm.LIQIU).minusDays(1)))
        assertEquals(Season.AUTUMN, seasonOf(solarTermDate(2026, SolarTerm.LIDONG).minusDays(1)))
    }

    @Test
    fun `new year and year end both fall in winter`() {
        assertEquals(Season.WINTER, seasonOf(LocalDate.of(2026, 1, 1)))
        assertEquals(Season.WINTER, seasonOf(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `leap years do not shift the season boundaries`() {
        // 2024 是闰年：立春仍是 2/4（若闰年取整写错会算成 2/3）
        assertEquals(LocalDate.of(2024, 2, 4), solarTermDate(2024, SolarTerm.LICHUN))
        assertEquals(LocalDate.of(2028, 2, 4), solarTermDate(2028, SolarTerm.LICHUN))
    }

    @Test
    fun `known years match the published dates`() {
        assertEquals(LocalDate.of(2025, 2, 3), solarTermDate(2025, SolarTerm.LICHUN))
        assertEquals(LocalDate.of(2026, 5, 5), solarTermDate(2026, SolarTerm.LIXIA))
        assertEquals(LocalDate.of(2026, 8, 7), solarTermDate(2026, SolarTerm.LIQIU))
        assertEquals(LocalDate.of(2026, 11, 7), solarTermDate(2026, SolarTerm.LIDONG))
    }

    @Test
    fun `every term stays inside its month for the whole century`() {
        for (year in 2000..2099) {
            SolarTerm.entries.forEach { term ->
                val date = solarTermDate(year, term)
                assertEquals("$year $term", term.month, date.monthValue)
                assertTrue("$year $term -> $date", date.dayOfMonth in 3..8)
            }
        }
    }
}
