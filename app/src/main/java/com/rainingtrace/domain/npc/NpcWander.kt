package com.rainingtrace.domain.npc

import com.rainingtrace.domain.map.WorldCoordinate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 停留期的小范围走动（"他站在那儿，但没完全不动"）。
 *
 * 为什么不是真随机：本作的确定性铁律（见 `SpawnPlanner`）要求"同一天同一时刻同结果"，
 * 否则每 10 秒刷新一次地图，NPC 会在原地胡乱横跳。这里把一次停留切成
 * [SLOT_MINUTES] 分钟一档，**同一档内位置固定**；档与档之间用前 [TRANSITION_MINUTES]
 * 分钟缓慢漂移过去，看起来像在附近踱步而不是瞬移。
 *
 * 偏移种子 = `(npcId, 日期键, 该条作息的到达时刻, 档号)`：换一天、换一段停留都会变，
 * 但当天重开 App 完全一致。
 */
object NpcWander {

    /** 活动半径：校园尺度下"就在附近"的感觉，不至于跑到另一个地点去。 */
    const val RADIUS_METERS = 14.0

    /** 每档多久换一次站位。 */
    const val SLOT_MINUTES = 20

    /** 档内前几分钟从上一档漂移过来（避免整点瞬移）。 */
    const val TRANSITION_MINUTES = 3

    private const val METERS_PER_DEGREE_LAT = 111_320.0
    private const val MIX_SEED = 0x243F6A8885A308D3L
    private const val MIX_MUL = 0x2545F4914F6CDD1DL

    /**
     * 求某个停留点在第 [minuteOfDay] 分钟时的实际站位。
     *
     * @param anchorMinute 这条作息的到达时刻（用它把"档"锚在作息段内）
     * @return 基础坐标 + 确定性偏移；半径恒 ≤ [RADIUS_METERS]
     */
    fun coordinateAt(
        base: WorldCoordinate,
        npcId: String,
        dateKey: String,
        anchorMinute: Int,
        minuteOfDay: Int,
    ): WorldCoordinate {
        val elapsed = ((minuteOfDay - anchorMinute) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val slot = elapsed / SLOT_MINUTES
        val withinSlot = elapsed % SLOT_MINUTES

        // 档内前几分钟：从上一档漂到本档；第一档直接到位（他刚走到这里）。
        val offset = if (slot > 0 && withinSlot < TRANSITION_MINUTES) {
            val t = smoothStep(withinSlot.toDouble() / TRANSITION_MINUTES)
            lerp(offsetFor(npcId, dateKey, anchorMinute, slot - 1), offsetFor(npcId, dateKey, anchorMinute, slot), t)
        } else {
            offsetFor(npcId, dateKey, anchorMinute, slot)
        }
        return applyOffset(base, offset.first, offset.second)
    }

    /** 档 [slot] 的偏移（北向米, 东向米）。 */
    private fun offsetFor(npcId: String, dateKey: String, anchorMinute: Int, slot: Int): Pair<Double, Double> {
        val seed = mix(hashOf(npcId), hashOf(dateKey), anchorMinute.toLong(), slot.toLong())
        val angle = unit(seed, 0L) * 2.0 * PI
        // 开方保证圆内均匀；再抬到 35%~100%，避免所有人都挤在圆心附近。
        val radius = sqrt(unit(seed, 1L)) * RADIUS_METERS * 0.65 + RADIUS_METERS * 0.35
        return (radius * cos(angle)) to (radius * sin(angle))
    }

    private fun applyOffset(base: WorldCoordinate, northMeters: Double, eastMeters: Double): WorldCoordinate {
        val lngScale = METERS_PER_DEGREE_LAT * cos(Math.toRadians(base.latDegrees)).coerceAtLeast(0.2)
        return WorldCoordinate(
            latDegrees = (base.latDegrees + northMeters / METERS_PER_DEGREE_LAT).coerceIn(-90.0, 90.0),
            lngDegrees = (base.lngDegrees + eastMeters / lngScale).coerceIn(-180.0, 180.0),
        )
    }

    /** 平滑过渡，避免线性插值的"折角"。 */
    private fun smoothStep(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * (3 - 2 * x)
    }

    private fun lerp(from: Pair<Double, Double>, to: Pair<Double, Double>, t: Double): Pair<Double, Double> =
        (from.first + (to.first - from.first) * t) to (from.second + (to.second - from.second) * t)

    // ---- 确定性随机：与 SpawnPlanner 同一口径（逐个 part 雪崩）----

    private fun unit(seed: Long, salt: Long): Double =
        (mix(seed, salt) ushr 11).toDouble() / (1L shl 53).toDouble()

    private fun mix(vararg parts: Long): Long {
        var h = MIX_SEED
        for (part in parts) {
            h = avalanche(h xor part)
        }
        return h
    }

    private fun hashOf(text: String): Long {
        var h = MIX_SEED
        for (ch in text) {
            h = avalanche(h xor ch.code.toLong())
        }
        return h
    }

    private fun avalanche(value: Long): Long {
        var z = value
        z = (z xor (z ushr 30)) * MIX_MUL
        z = (z xor (z ushr 27)) * MIX_MUL
        return z xor (z ushr 31)
    }
}
