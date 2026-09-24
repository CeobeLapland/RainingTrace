package com.rainingtrace.platform.location

import com.rainingtrace.domain.map.LocationCadenceController
import com.rainingtrace.domain.map.LocationHealth
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.RawLocationFix
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.settings.LocationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 在 Fake / GPS 两种定位来源间热切换的 [LocationProvider]。
 *
 * - 设置里的 [LocationMode] 变化自动切换；
 * - Fake 模式：[emitDebugMove] 透传点击地图的调试移动；
 * - GPS 模式：启动系统定位，调试点击无效；
 * - 授权状态变化（刚被授予权限）时调 [refresh] 重新拉起 GPS 采集。
 */
class SwitchableLocationProvider(
    private val fake: FakeLocationProvider,
    private val android: AndroidLocationProvider,
    initialMode: LocationMode,
    parentScope: CoroutineScope,
    modeFlow: Flow<LocationMode>,
) : LocationProvider, LocationCadenceController, LocationHealth {

    private val _updates = MutableSharedFlow<RawLocationFix>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val updates: Flow<RawLocationFix> = _updates.asSharedFlow()

    @Volatile
    var mode: LocationMode = initialMode
        private set

    private val scope = parentScope
    private var collectJob: Job? = null

    /**
     * 定位卡住的信号：直接在 android 的实现上委托。
     * Fake 模式下恒为 false（那是调试输入，不存在"信号"这回事）。
     */
    override val stalled: StateFlow<Boolean> =
        combine(android.stalled, modeFlow) { stalled, current ->
            current == LocationMode.GPS && stalled
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(STALLED_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = false,
        )

    init {
        // 立即接上初始来源（Fake 的 replay 点马上能转发），不等 DataStore 首帧
        switchTo(initialMode)
        scope.launch {
            modeFlow.distinctUntilChanged().collect { switchTo(it) }
        }
    }

    override val latest: RawLocationFix?
        get() = when (mode) {
            LocationMode.FAKE -> fake.latest
            LocationMode.GPS -> android.latest
        }

    /** Fake 模式下的调试移动；GPS 模式忽略。 */
    fun emitDebugMove(coordinate: WorldCoordinate) {
        if (mode == LocationMode.FAKE) fake.emit(coordinate)
    }

    /** 权限可能刚被授予/撤销时调用：按当前模式重启采集。 */
    fun refresh() {
        switchTo(mode)
    }

    /** 后台低频档只在真实定位上有意义；Fake 是调试输入。 */
    override fun setPassiveIntervalMs(intervalMs: Long?) {
        android.setPassiveIntervalMs(intervalMs)
    }

    private fun switchTo(newMode: LocationMode) {
        mode = newMode
        collectJob?.cancel()
        when (newMode) {
            LocationMode.FAKE -> {
                android.stop()
                collectJob = scope.launch {
                    fake.updates.collect { _updates.tryEmit(it) }
                }
                // 切回 Fake 立即重放最近位置
                fake.latest?.let { _updates.tryEmit(it) }
            }
            LocationMode.GPS -> {
                collectJob = scope.launch {
                    android.updates.collect { _updates.tryEmit(it) }
                }
                android.start()
            }
        }
    }

    private companion object {
        const val STALLED_SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
