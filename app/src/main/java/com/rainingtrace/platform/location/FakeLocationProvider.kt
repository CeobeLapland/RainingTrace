package com.rainingtrace.platform.location

import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.RawLocationFix
import com.rainingtrace.domain.map.WorldCoordinate
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.rainingtrace.core.time.WorldClock

/**
 * RT-LOC-002: Fake 定位实现。
 *
 * MVP 阶段"位置"由外部注入（调试 UI 点击地图、测试脚本等）。
 * 这是无 GPS 环境下可玩 Fake World 的关键（00_总纲 原则 C）。
 */
class FakeLocationProvider(
    private val clock: WorldClock,
    initial: WorldCoordinate? = null,
) : LocationProvider {

    private val _updates = MutableSharedFlow<RawLocationFix>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val updates: Flow<RawLocationFix> = _updates

    override val latest: RawLocationFix?
        get() = lastFix

    var lastFix: RawLocationFix? = null
        private set

    fun emit(coordinate: WorldCoordinate, accuracyMeters: Double = 5.0) {
        val fix = RawLocationFix(
            coordinate = coordinate,
            accuracyMeters = accuracyMeters,
            timestampEpochMs = clock.now().toEpochMilli(),
            source = LocationSource.FAKE,
        )
        lastFix = fix
        _updates.tryEmit(fix)
    }

    init {
        initial?.let { emit(it) }
    }
}
