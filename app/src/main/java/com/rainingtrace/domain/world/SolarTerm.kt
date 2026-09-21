package com.rainingtrace.domain.world

import java.time.LocalDate
import kotlin.math.floor

/**
 * 二十四节气里的四个"分界"节气：立春 / 立夏 / 立秋 / 立冬。
 *
 * 季节口径定为**节气**（GDD §07 季节），而不是简单按月份切：
 * 现实里的四季感是由节气给的，这更贴合"现实即输入"。
 */
enum class SolarTerm(val month: Int) {
    LICHUN(2),
    LIXIA(5),
    LIQIU(8),
    LIDONG(11),
}

/**
 * 节气日期近似算法（21 世纪通用的"寿星公式"）：
 *
 * `day = floor(Y * 0.2422 + C) - floor((Y - leapOffset) / 4)`
 *
 * - `Y` = 年份后两位（[LocalDate.year] % 100）
 * - `C` = 该节气的世纪常量
 * - `leapOffset` = 闰年数取整的偏移。**立春取 1，其余取 0**：这是文献里
 *   立春单独用 `(Y-1)/4` 的规定，不这样取的话 2000/2020/2024 等年份会早一天。
 *
 * 精度：±1 天。季节边界差一天对玩法（"秋日松果"能不能采）没有实际影响，
 * 所以不做星历计算。若将来需要零误差，把本函数换成一张离线生成的日期表即可，
 * 调用方（[seasonOf] / [DerivedSeasonSource]）不用改。
 *
 * 仅对 21 世纪（2000–2099）有效：`Y = year % 100` 在 2100 年会回到 0，
 * 而 22 世纪的 C 常量不同，届时需要一并替换。
 */
fun solarTermDay(year: Int, term: SolarTerm): Int {
    val y = year % 100
    val (constant, leapOffset) = when (term) {
        SolarTerm.LICHUN -> 3.87 to 1
        SolarTerm.LIXIA -> 5.52 to 0
        SolarTerm.LIQIU -> 7.5 to 0
        SolarTerm.LIDONG -> 7.438 to 0
    }
    return floor(y * 0.2422 + constant).toInt() - floor((y - leapOffset) / 4.0).toInt()
}

/** 该年的节气日期。 */
fun solarTermDate(year: Int, term: SolarTerm): LocalDate =
    LocalDate.of(year, term.month, solarTermDay(year, term))

/**
 * 按节气推导季节：立春→春、立夏→夏、立秋→秋、立冬→冬。
 *
 * 用 `date.year` 取节气年份，所以 1 月/12 月自然落在冬季（冬至到立春之间），
 * 不需要额外跨年特判。
 */
fun seasonOf(date: LocalDate): Season {
    val year = date.year
    return when {
        date < solarTermDate(year, SolarTerm.LICHUN) -> Season.WINTER
        date < solarTermDate(year, SolarTerm.LIXIA) -> Season.SPRING
        date < solarTermDate(year, SolarTerm.LIQIU) -> Season.SUMMER
        date < solarTermDate(year, SolarTerm.LIDONG) -> Season.AUTUMN
        else -> Season.WINTER
    }
}
