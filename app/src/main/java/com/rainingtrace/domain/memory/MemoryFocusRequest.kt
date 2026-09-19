package com.rainingtrace.domain.memory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 日记 →「在地图查看」的一次性聚焦请求。
 *
 * 写方（日记）调用 [request]，读方（地图）读 [memory] 并 [consume]。
 * 用一个轻量持有者而不是给导航图加参数：地图是常驻 Tab，
 * 带参数的导航会和现有 saveState / restoreState 的 Tab 切换逻辑打架。
 */
class MemoryFocusRequest {

    private val _memory = MutableStateFlow<MemoryNode?>(null)
    val memory: StateFlow<MemoryNode?> = _memory.asStateFlow()

    fun request(memory: MemoryNode) {
        _memory.value = memory
    }

    fun consume() {
        _memory.value = null
    }
}
