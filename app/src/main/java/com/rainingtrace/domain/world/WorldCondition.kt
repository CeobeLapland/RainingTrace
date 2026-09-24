package com.rainingtrace.domain.world

/**
 * 世界状态条件（GDD §07 / §09：天气、时间、季节影响产出与出现）。
 *
 * 纯函数判定：给定 [WorldState] 就能算出真假——可单测、可枚举化、
 * 也是将来到开发者编辑器里配"只在某条件下出现"的数据形态（GDD §21）。
 *
 * 只收现在真正要用的几种；需要"夜晚 + 雨"这类组合用 [All] 叠加，
 * 不要为想象中的需求先造一堆类型。
 */
sealed interface WorldCondition {

    fun isSatisfiedBy(state: WorldState): Boolean

    /**
     * 这条条件的"具体度"权重：条件越具体，产出规则的优先级越高
     * （见 `ResourceYieldRule.specificity`）。
     *
     * [Not] 覆写为 0：否定条件说的是"不要什么"，比正向条件**更不具体**。
     * 不这样做的话，`Not(雨天)` 会和"雨天湖边"拿同样的分甚至压过它，
     * 优先级方向就完全反了。
     */
    val weight: Int get() = 1

    /** 天气属于给定集合。 */
    data class WeatherIn(val kinds: Set<WeatherKind>) : WorldCondition {
        init {
            require(kinds.isNotEmpty()) { "WeatherIn needs at least one kind" }
        }

        override fun isSatisfiedBy(state: WorldState): Boolean = state.weather.kind in kinds
    }

    /** 时段属于给定集合。 */
    data class TimeOfDayIn(val times: Set<TimeOfDay>) : WorldCondition {
        init {
            require(times.isNotEmpty()) { "TimeOfDayIn needs at least one time of day" }
        }

        override fun isSatisfiedBy(state: WorldState): Boolean = state.timeOfDay in times
    }

    /** 季节属于给定集合；季节未确定（null）时**一律不满足**，不给"猜"的机会。 */
    data class SeasonIn(val seasons: Set<Season>) : WorldCondition {
        init {
            require(seasons.isNotEmpty()) { "SeasonIn needs at least one season" }
        }

        override fun isSatisfiedBy(state: WorldState): Boolean =
            state.season != null && state.season in seasons
    }

    /**
     * 本地时间区间，左闭右开 [start, end)，按"当天第几分钟"表达。
     * end 小于 start 时自动跨零点，例如 22:00–02:00。
     */
    data class BetweenMinutes(
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
    ) : WorldCondition {
        init {
            require(startMinuteOfDay in 0..MAX_MINUTE) { "start out of range: $startMinuteOfDay" }
            require(endMinuteOfDay in 0..MINUTES_PER_DAY) { "end out of range: $endMinuteOfDay" }
            require(startMinuteOfDay != endMinuteOfDay) { "empty window is not a condition" }
        }

        override fun isSatisfiedBy(state: WorldState): Boolean {
            val minute = state.minuteOfDay
            return if (startMinuteOfDay < endMinuteOfDay) {
                minute >= startMinuteOfDay && minute < endMinuteOfDay
            } else {
                minute >= startMinuteOfDay || minute < endMinuteOfDay
            }
        }

        private companion object {
            const val MINUTES_PER_DAY = 24 * 60
            const val MAX_MINUTE = MINUTES_PER_DAY - 1
        }
    }

    /** 全部满足（AND）。空列表 = 无条件，恒真。 */
    data class All(val conditions: List<WorldCondition>) : WorldCondition {
        override fun isSatisfiedBy(state: WorldState): Boolean =
            conditions.all { it.isSatisfiedBy(state) }
    }

    /**
     * 取反（GDD §21：内容要能配"不下雨的时候"）。
     *
     * 语义就是布尔取反，**不做三值逻辑**：`Not(SeasonIn(AUTUMN))` 在
     * 季节未确定（`season == null`）时为**真**。这是唯一会让作者意外的地方，写死在这里。
     *
     * 权重为 0（见 [weight]），所以否定条件不会抢过正向限定。
     */
    data class Not(val condition: WorldCondition) : WorldCondition {
        override val weight: Int get() = 0

        override fun isSatisfiedBy(state: WorldState): Boolean = !condition.isSatisfiedBy(state)
    }
}

/** 便捷：一组条件是否全部满足（空组为真）。 */
fun List<WorldCondition>.allSatisfiedBy(state: WorldState): Boolean =
    all { it.isSatisfiedBy(state) }

/** 便捷：取"会下雨"的天气集合，供雨天条件使用。 */
val RAINY_WEATHER: WorldCondition = WorldCondition.WeatherIn(WeatherKind.RAINY)