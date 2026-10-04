package com.rainingtrace.domain.spawn

import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceOrigin
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.defaultActionsFor
import com.rainingtrace.domain.world.WorldState
import com.rainingtrace.domain.world.allSatisfiedBy

/**
 * 资源点刷新的确定性 planner（纯函数，可单测）。
 *
 * **不落库**：给定同一份规则与同一天的世界状态，永远算出同一批点；
 * 重启、换机、重装都一致（见 `HANDOFF_4 §6` 的"确定性重算"路线）。
 *
 * 三条刻意的设计：
 * 1. **偏移固定**：点内偏移的种子只用 `(规则, 候选点序号)`，不含日期。
 *    同一个候选点永远偏到同一个真实坐标——玩家能记住"那棵树下有莓子"，
 *    点不会天天挪窝，也才能当路标。
 * 2. **只有"今天激活哪几个"随日期变**：种子含日期。
 * 3. **世界状态只做门控**：天气/季节/时段只通过 [SpawnRule.conditions] 决定
 *    "此刻能不能采"，不参与挑选——否则天气一变，整片点会瞬移。
 *
 * 地点 id 跨天稳定（`place.spawn.<规则名>.<候选点序号>`），所以 footprint 冷却
 * （按 `placeId` 判）不会被"每天换 id"重置，图鉴按资源聚合也不会重复计数。
 */
object SpawnPlanner {

    private const val MINUTES_PER_DAY = SpawnRule.MINUTES_PER_DAY
    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * 刚过期还留一点宽限：地图刷新是每分钟一次，玩家点的可能是上一帧的图标。
     * 没有它就会出现"图标还在、点下去没反应"。
     */
    private const val GRACE_MS = 2 * 60 * 1000L

    /** 米→度的纬度换算（与 `MapViewport.expanded` 同一个球形近似）。 */
    private const val METERS_PER_DEGREE_LAT = 111_320.0

    private const val PLACE_ID_PREFIX = "place.spawn."

    /** 该规则此刻所有"存在"的点（已过宽限期的不算）。 */
    fun plan(rules: List<SpawnRule>, state: WorldState): List<Place> =
        rules.flatMap { planRule(it, state) }

    /** 地点 id 与日期无关，方便跨天复用同一份冷却记录。 */
    fun placeId(ruleId: String, spotIndex: Int): String =
        PLACE_ID_PREFIX + ruleKey(ruleId) + "." + spotIndex

    private fun planRule(rule: SpawnRule, state: WorldState): List<Place> {
        if (!rule.conditions.allSatisfiedBy(state)) return emptyList()

        val day = state.localDate.toEpochDay()
        val dayStartMs = state.localDate.atStartOfDay(WORLD_ZONE).toInstant().toEpochMilli()
        val nowMs = state.instant.toEpochMilli()
        val count = minOf(rule.perDay, rule.spots.size)

        return pickSpots(rule.id, day, rule.spots.size, count).mapNotNull { index ->
            val startMinute = startMinuteOf(rule, day, index)
            val startsAt = dayStartMs + startMinute * MILLIS_PER_MINUTE
            val expiresAt = startsAt + rule.lifetimeMinutes * MILLIS_PER_MINUTE
            if (nowMs < startsAt || nowMs >= expiresAt + GRACE_MS) return@mapNotNull null

            val spot = rule.spots[index]
            Place(
                id = placeId(rule.id, index),
                name = rule.name,
                type = rule.placeType,
                coordinate = jitter(rule.id, index, spot),
                actions = rule.actions ?: defaultActionsFor(rule.placeType),
                description = rule.description,
                origin = PlaceOrigin.SPAWNED,
                expiresAtEpochMs = expiresAt,
            )
        }
    }

    /**
     * 今天激活哪几个候选点：Fisher–Yates 用确定性随机洗牌后取前 [count] 个。
     * 候选点不够时全上（此时没有随机，是数据太少，不是 bug）。
     */
    private fun pickSpots(ruleId: String, day: Long, spotCount: Int, count: Int): List<Int> {
        if (count >= spotCount) return (0 until spotCount).toList()
        val seed = mix(hashOf(ruleId), day, SALT_PICK)
        val indices = MutableList(spotCount) { it }
        for (i in spotCount - 1 downTo 1) {
            val j = (unit(seed, i.toLong()) * (i + 1)).toInt().coerceIn(0, i)
            val swap = indices[i]
            indices[i] = indices[j]
            indices[j] = swap
        }
        return indices.take(count)
    }

    /** 当天的出现时刻（第几分钟）。整天时恒为 0，所以不会把窗口顶到明天。 */
    private fun startMinuteOf(rule: SpawnRule, day: Long, spotIndex: Int): Int {
        val slack = MINUTES_PER_DAY - rule.lifetimeMinutes
        if (slack <= 0) return 0
        val seed = mix(hashOf(rule.id), day, SALT_START, spotIndex.toLong())
        return (unit(seed, spotIndex.toLong()) * (slack + 1)).toInt().coerceIn(0, slack)
    }

    /** 点内偏移：极坐标一个角度 + 一个半径（开方保证圆内均匀）。 */
    private fun jitter(ruleId: String, spotIndex: Int, spot: SpawnSpot): WorldCoordinate {
        val seed = mix(hashOf(ruleId), spotIndex.toLong(), SALT_JITTER)
        val angle = unit(seed, 0L) * 2.0 * Math.PI
        val distance = kotlin.math.sqrt(unit(seed, 1L)) * spot.radiusMeters

        val lngScale = METERS_PER_DEGREE_LAT *
            kotlin.math.cos(Math.toRadians(spot.coordinate.latDegrees)).coerceAtLeast(0.2)
        val lat = spot.coordinate.latDegrees + distance * kotlin.math.cos(angle) / METERS_PER_DEGREE_LAT
        val lng = spot.coordinate.lngDegrees + distance * kotlin.math.sin(angle) / lngScale
        return WorldCoordinate(
            latDegrees = lat.coerceIn(-90.0, 90.0),
            lngDegrees = lng.coerceIn(-180.0, 180.0),
        )
    }

    private fun ruleKey(ruleId: String): String =
        ruleId.removePrefix("spawn.").ifBlank { ruleId }

    // ---- 确定性随机：只用位运算与乘法，跨平台稳定（不用 java.util.Random）----

    private const val SALT_PICK = 0x11L
    private const val SALT_START = 0x22L
    private const val SALT_JITTER = 0x33L

    /** 混合器初值（足够"没有规律"即可，不需要是某个常数）。 */
    private const val MIX_SEED = 0x243F6A8885A308D3L

    /** 雪崩用的奇乘子。 */
    private const val MIX_MUL = 0x2545F4914F6CDD1DL

    /** [0, 1) 的确定性小数（取混合值的高 53 位）。 */
    private fun unit(seed: Long, salt: Long): Double =
        (mix(seed, salt) ushr 11).toDouble() / (1L shl 53).toDouble()

    /** 依次把每个 part 混进来；同输入必得同输出。 */
    private fun mix(vararg parts: Long): Long {
        var h = MIX_SEED
        for (part in parts) {
            h = avalanche(h xor part)
        }
        return h
    }

    /** FNV-1a 风格：把规则 id 变成稳定的长整数种子。 */
    private fun hashOf(text: String): Long {
        var h = MIX_SEED
        for (ch in text) {
            h = avalanche(h xor ch.code.toLong())
        }
        return h
    }

    /**
     * splitmix64 的收尾混合。必须逐个 part 雪崩，否则"小 salt"（循环下标 0/1/2…）
     * 只动到低位，取高 53 位时几乎不变——洗牌会退化成几乎不动。
     */
    private fun avalanche(value: Long): Long {
        var z = value
        z = (z xor (z ushr 30)) * MIX_MUL
        z = (z xor (z ushr 27)) * MIX_MUL
        return z xor (z ushr 31)
    }
}