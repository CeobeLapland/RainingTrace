package com.rainingtrace.domain.world

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldConditionTest {

    private fun state(
        hour: Int,
        kind: WeatherKind = WeatherKind.CLEAR,
        season: Season? = null,
        holiday: String? = null,
    ) = deriveWorldState(
        instant = LocalDate.of(2026, 9, 19).atTime(hour, 0).toInstant(ZoneOffset.ofHours(8)),
        weather = WeatherState(kind),
        season = season,
        holiday = holiday,
    )

    @Test
    fun `weather condition matches only listed kinds`() {
        val rainy = WorldCondition.WeatherIn(setOf(WeatherKind.LIGHT_RAIN, WeatherKind.HEAVY_RAIN))

        assertTrue(rainy.isSatisfiedBy(state(12, WeatherKind.LIGHT_RAIN)))
        assertTrue(rainy.isSatisfiedBy(state(12, WeatherKind.HEAVY_RAIN)))
        assertFalse(rainy.isSatisfiedBy(state(12, WeatherKind.CLEAR)))
    }

    @Test
    fun `time of day condition matches the derived bucket`() {
        val night = WorldCondition.TimeOfDayIn(setOf(TimeOfDay.NIGHT))

        assertTrue(night.isSatisfiedBy(state(23)))
        assertTrue(night.isSatisfiedBy(state(3)))
        assertFalse(night.isSatisfiedBy(state(12)))
    }

    @Test
    fun `season condition is false while season is undetermined`() {
        val autumn = WorldCondition.SeasonIn(setOf(Season.AUTUMN))

        // 季节规则没定 → 不猜，条件不满足
        assertFalse(autumn.isSatisfiedBy(state(12, season = null)))
        assertTrue(autumn.isSatisfiedBy(state(12, season = Season.AUTUMN)))
        assertFalse(autumn.isSatisfiedBy(state(12, season = Season.WINTER)))
    }

    @Test
    fun `between minutes is start inclusive end exclusive`() {
        val morning = WorldCondition.BetweenMinutes(6 * 60, 9 * 60)

        assertFalse(morning.isSatisfiedBy(state(5)))
        assertTrue(morning.isSatisfiedBy(state(6)))
        assertTrue(morning.isSatisfiedBy(state(8)))
        assertFalse(morning.isSatisfiedBy(state(9)))
    }

    @Test
    fun `between minutes wraps over midnight`() {
        val lateNight = WorldCondition.BetweenMinutes(22 * 60, 2 * 60)

        assertTrue(lateNight.isSatisfiedBy(state(23)))
        assertTrue(lateNight.isSatisfiedBy(state(0)))
        assertTrue(lateNight.isSatisfiedBy(state(1)))
        assertFalse(lateNight.isSatisfiedBy(state(2)))
        assertFalse(lateNight.isSatisfiedBy(state(12)))
    }

    @Test
    fun `all requires every condition and empty means unconditional`() {
        val rainyNight = WorldCondition.All(
            listOf(
                WorldCondition.WeatherIn(WeatherKind.RAINY),
                WorldCondition.TimeOfDayIn(setOf(TimeOfDay.NIGHT)),
            ),
        )

        assertTrue(rainyNight.isSatisfiedBy(state(22, WeatherKind.LIGHT_RAIN)))
        assertFalse(rainyNight.isSatisfiedBy(state(12, WeatherKind.LIGHT_RAIN)))
        assertFalse(rainyNight.isSatisfiedBy(state(22, WeatherKind.CLEAR)))
        assertTrue(WorldCondition.All(emptyList()).isSatisfiedBy(state(12)))
        assertTrue(emptyList<WorldCondition>().allSatisfiedBy(state(12)))
    }

    @Test
    fun `rainy weather shorthand covers exactly the rainy kinds`() {
        assertTrue(RAINY_WEATHER.isSatisfiedBy(state(12, WeatherKind.LIGHT_RAIN)))
        assertTrue(RAINY_WEATHER.isSatisfiedBy(state(12, WeatherKind.HEAVY_RAIN)))
        assertFalse(RAINY_WEATHER.isSatisfiedBy(state(12, WeatherKind.SNOW)))
        assertFalse(RAINY_WEATHER.isSatisfiedBy(state(12, WeatherKind.CLEAR)))
    }

    @Test
    fun `not is a plain boolean negation`() {
        val notRainy = WorldCondition.Not(WorldCondition.WeatherIn(WeatherKind.RAINY))

        assertTrue(notRainy.isSatisfiedBy(state(12, WeatherKind.CLEAR)))
        assertTrue(notRainy.isSatisfiedBy(state(12, WeatherKind.SNOW)))
        assertFalse(notRainy.isSatisfiedBy(state(12, WeatherKind.LIGHT_RAIN)))
    }

    /**
     * 这是唯一会让作者意外的地方，所以写死成测试：`Not` 不做三值逻辑，
     * 季节未确定时 `Not(SeasonIn(AUTUMN))` 是**真**。
     */
    @Test
    fun `not of an undetermined season is true`() {
        val notAutumn = WorldCondition.Not(WorldCondition.SeasonIn(setOf(Season.AUTUMN)))

        assertTrue(notAutumn.isSatisfiedBy(state(12, season = null)))
        assertFalse(notAutumn.isSatisfiedBy(state(12, season = Season.AUTUMN)))
        assertTrue(notAutumn.isSatisfiedBy(state(12, season = Season.WINTER)))
    }

    /**
     * 否定条件的权重是 0：否则"不下雨"会和"雨天湖边"一样具体，
     * 优先级方向就反了（见 ResourceYieldRule.specificity）。
     */
    @Test
    fun `not weighs less than a positive condition`() {
        val positive = WorldCondition.WeatherIn(WeatherKind.RAINY)
        val negative = WorldCondition.Not(positive)

        assertTrue(negative.weight < positive.weight)
        assertEquals(0, negative.weight)
    }
}