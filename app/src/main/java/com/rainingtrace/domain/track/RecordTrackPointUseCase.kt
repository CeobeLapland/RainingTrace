package com.rainingtrace.domain.track

import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.RawLocationFix
import com.rainingtrace.domain.map.distanceMetersTo
import java.util.UUID

/**
 * 原始定位 → 稳定轨迹点的去噪闸门（06_地图专项 §4：Raw → Filter → Stable）。
 *
 * 规则（全部基于上一条**已接受**的点）：
 * - 精度差于 [MAX_ACCURACY_METERS]：丢弃（室内漂移保护）；
 * - 时间戳过旧或来自未来：丢弃（迟到/时钟异常 fix）；
 * - 位移小于 [MIN_MOVEMENT_METERS]：丢弃（静止去抖）；
 * - 速度超过 [MAX_SPEED_MPS]：丢弃（瞬移/跳点）；
 * - 第一个点总是接受。
 *
 * 纯 Kotlin、时钟可注入；随机性不参与本用例。
 */
class RecordTrackPointUseCase(
    private val trackRepository: TrackRepository,
    private val clock: WorldClock,
) {
    suspend operator fun invoke(fix: RawLocationFix): RecordTrackResult {
        val now = clock.now().toEpochMilli()

        // 精度过滤只针对真实 GPS；Fake 点是显式注入的可信调试输入。
        if (fix.source == LocationSource.GPS && fix.accuracyMeters > MAX_ACCURACY_METERS) {
            return RecordTrackResult.Rejected(RejectReason.POOR_ACCURACY)
        }
        if (now - fix.timestampEpochMs > MAX_AGE_MS) {
            return RecordTrackResult.Rejected(RejectReason.STALE)
        }
        if (fix.timestampEpochMs - now > MAX_FUTURE_MS) {
            return RecordTrackResult.Rejected(RejectReason.FUTURE_TIMESTAMP)
        }

        val last = trackRepository.latestPoint()
        if (last != null) {
            val dtMs = fix.timestampEpochMs - last.timestampEpochMs
            if (dtMs <= 0L) {
                return RecordTrackResult.Rejected(RejectReason.NOT_MOVING_FORWARD)
            }
            val distanceM = fix.coordinate.distanceMetersTo(last.coordinate)
            if (distanceM < MIN_MOVEMENT_METERS) {
                return RecordTrackResult.Rejected(RejectReason.TOO_CLOSE)
            }
            // 瞬移检查只针对真实 GPS；Fake 模式下"点地图移动"允许任意距离。
            if (fix.source == LocationSource.GPS) {
                val speedMps = distanceM / (dtMs / 1000.0)
                if (speedMps > MAX_SPEED_MPS) {
                    return RecordTrackResult.Rejected(RejectReason.IMPLAUSIBLE_JUMP)
                }
            }
        }

        val point = TrackPoint(
            id = UUID.randomUUID().toString(),
            timestampEpochMs = fix.timestampEpochMs,
            coordinate = fix.coordinate,
            accuracyMeters = fix.accuracyMeters,
            source = fix.source,
        )
        trackRepository.append(point)
        return RecordTrackResult.Accepted(point)
    }

    companion object {
        const val MAX_ACCURACY_METERS = 50.0
        const val MIN_MOVEMENT_METERS = 8.0
        const val MAX_SPEED_MPS = 35.0 // 126km/h，步行/骑行场景的宽松上限
        const val MAX_AGE_MS = 30_000L
        const val MAX_FUTURE_MS = 10_000L
    }
}

sealed interface RecordTrackResult {
    data class Accepted(val point: TrackPoint) : RecordTrackResult
    data class Rejected(val reason: RejectReason) : RecordTrackResult
}

enum class RejectReason {
    POOR_ACCURACY,
    STALE,
    FUTURE_TIMESTAMP,
    NOT_MOVING_FORWARD,
    TOO_CLOSE,
    IMPLAUSIBLE_JUMP,
}
