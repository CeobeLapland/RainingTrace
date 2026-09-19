package com.rainingtrace.core.lifecycle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用前后台状态（单进程内唯一真相）。
 *
 * 为什么需要它：地图屏的处理流（去噪写库 → 开雾 → 渲染）原来在 viewModelScope 里
 * 无条件运行，进程退到后台也不会停；这会变成后台耗电大户。
 * 有了这个状态，地图侧可以按前台闸门处理，后台只留"低频记录"一件事。
 *
 * 用 started/stopped 计数而不是 resumed/paused：可见性才是我们关心的边界，
 * 系统权限弹窗、相机预览等瞬时 pause 不应该被当成"进入后台"。
 */
class AppForegroundState {

    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private var startedCount = 0

    fun onActivityStarted() {
        startedCount += 1
        if (startedCount > 0) _isForeground.value = true
    }

    fun onActivityStopped() {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        if (startedCount == 0) _isForeground.value = false
    }
}