package com.rainingtrace.domain.track

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * 轨迹日历 →「在地图查看」的一次性聚焦请求。
 *
 * 与 [com.rainingtrace.domain.memory.MemoryFocusRequest] 同构：写方 request，读方 collect + consume。
 * 地图是常驻 Tab，带参数的导航会和 saveState / restoreState 的 Tab 切换打架。
 */
class TrackDayFocusRequest {

    private val _date = MutableStateFlow<LocalDate?>(null)
    val date: StateFlow<LocalDate?> = _date.asStateFlow()

    fun request(date: LocalDate) {
        _date.value = date
    }

    fun consume() {
        _date.value = null
    }
}