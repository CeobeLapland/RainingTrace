package com.rainingtrace.domain.spawn

import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceOrigin
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.WorldCondition
import com.rainingtrace.domain.world.deriveWorldState
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 刷新的确定性：**不落库**，所以"同一天同一份规则必须算出同一批点"是这条链的地基。
 * 这里把"跨天会换"、"点不挪窝"、"id 与日期无关（冷却才能跨天）"都钉住。
 */
class SpawnPlannerTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)

    private fun state(
        day: String = "2026-09-13",
        hour: Int = 12,
        kind: WeatherKind = WeatherKind.CLEAR,
        season: Season? = null,
    ) = deriveWorldState(
        instant = LocalDate.parse(day).atTime(hour, 0).toInstant(ZoneOffset.ofHours(8)),
        weather = WeatherState(kind),
        season = season,
    )

    private fun rule(
        id: String = "spawn.berry",
        spots: Int = 4,
        perDay: Int = 2,
        lifetimeMinutes: Int = SpawnRule.MINUTES_PER_DAY,
        conditions: List<WorldCondition> = emptyList(),
    ) = SpawnRule(
        id = id,
        name = "浆果丛",
        placeType = PlaceType.BERRY_BUSH,
        spots = List(spots) { index ->
            SpawnSpot(WorldCoordinate(39.7326 + index * 0.0005, 116.1712), 20.0)
        },
        perDay = perDay,
        lifetimeMinutes = lifetimeMinutes,
        conditions = conditions,
    )

    @Test
    fun `同一天同一规则算出完全一样的点`() {
        val r = rule()
        val noon = SpawnPlanner.plan(listOf(r), state(hour = 12))
        val evening = SpawnPlanner.plan(listOf(r), state(hour = 20))

        assertEquals(noon.map { it.id }, evening.map { it.id })
        assertEquals(noon.map { it.coordinate }, evening.map { it.coordinate })
    }

    @Test
    fun `每天只刷出 perDay 个候选点`() {
        val places = SpawnPlanner.plan(listOf(rule(spots = 5, perDay = 2)), state())

        assertEquals(2, places.size)
        assertEquals(2, places.map { it.id }.toSet().size)
    }

    @Test
    fun `候选点比 perDay 少时把候选点全刷出来`() {
        val places = SpawnPlanner.plan(listOf(rule(spots = 2, perDay = 9)), state())

        assertEquals(2, places.size)
    }

    @Test
    fun `偏移落在候选点的半径内`() {
        val r = rule(spots = 3, perDay = 3)

        SpawnPlanner.plan(listOf(r), state()).forEach { place ->
            val spot = r.spots[place.id.substringAfterLast('.').toInt()]
            val distance = place.coordinate.distanceMetersTo(spot.coordinate)
            assertTrue(
                "${place.id} 偏出了候选点半径：$distance > ${spot.radiusMeters}",
                distance <= spot.radiusMeters + 2.0,
            )
        }
    }

    @Test
    fun `同一个候选点的坐标跨天不挪窝`() {
        val r = rule(spots = 3, perDay = 3)
        val day1 = SpawnPlanner.plan(listOf(r), state(day = "2026-03-01")).associateBy { it.id }
        val day2 = SpawnPlanner.plan(listOf(r), state(day = "2026-03-02")).associateBy { it.id }

        val shared = day1.keys.intersect(day2.keys)
        assertTrue("两天之间应该有点是重复出现的", shared.isNotEmpty())
        // 否则玩家昨天记住的"那棵树下有莓子"今天就找不着了
        shared.forEach { id -> assertEquals(day1.getValue(id).coordinate, day2.getValue(id).coordinate) }
    }

    @Test
    fun `条件不满足时一个点都不刷`() {
        val rainy = rule(conditions = listOf(WorldCondition.WeatherIn(setOf(WeatherKind.LIGHT_RAIN))))

        assertTrue(SpawnPlanner.plan(listOf(rainy), state(kind = WeatherKind.CLEAR)).isEmpty())
        assertEquals(2, SpawnPlanner.plan(listOf(rainy), state(kind = WeatherKind.LIGHT_RAIN)).size)
    }

    @Test
    fun `刷出来的是 SPAWNED、动作非空、过期时刻落在当天内`() {
        val dayEnd = LocalDate.parse("2026-09-14")
            .atStartOfDay(ZoneOffset.ofHours(8))
            .toInstant()
            .toEpochMilli()

        val places = SpawnPlanner.plan(listOf(rule()), state(hour = 12))

        assertTrue(places.isNotEmpty())
        places.forEach { place ->
            assertEquals(PlaceOrigin.SPAWNED, place.origin)
            assertTrue(place.actions.isNotEmpty())
            assertEquals(setOf(PlaceActionType.COLLECT), place.actions)
            val expiresAt = place.expiresAtEpochMs
            assertNotNull(expiresAt)
            // 跨零点会和新一天的实例重叠，所以窗口必须夹在当天内
            assertTrue("过期时刻跑到明天了：$expiresAt", expiresAt!! <= dayEnd)
        }
    }

    @Test
    fun `短寿命的点只在当天的一个窗口里出现`() {
        val r = rule(spots = 1, perDay = 1, lifetimeMinutes = 60)

        val hoursWithSpawn = (0..23)
            .count { hour -> SpawnPlanner.plan(listOf(r), state(hour = hour)).isNotEmpty() }

        // 1 小时的窗口最多跨两个整点小时，不该占满整天
        assertTrue("1 小时的窗口占了 $hoursWithSpawn 个小时", hoursWithSpawn in 1..2)
    }

    @Test
    fun `地点 id 与日期无关，跨天能复用同一份冷却记录`() {
        val r = rule(spots = 3, perDay = 3)
        val day1 = SpawnPlanner.plan(listOf(r), state(day = "2026-03-01")).map { it.id }.toSet()
        val day2 = SpawnPlanner.plan(listOf(r), state(day = "2026-03-02")).map { it.id }.toSet()

        assertEquals("三天全上的时候 id 应当完全一致", day1, day2)
        day1.forEach { assertTrue(it.startsWith("place.spawn.berry.")) }
    }

    @Test
    fun `不同日期会换掉一部分激活的点`() {
        val r = rule(spots = 6, perDay = 2)
        val combos = (1..10)
            .map { day ->
                SpawnPlanner.plan(listOf(r), state(day = "2026-03-%02d".format(day)))
                    .map { it.id }
                    .toSet()
            }
            .toSet()

        assertTrue("十天里应当出现过不止一种组合", combos.size > 1)
    }
}