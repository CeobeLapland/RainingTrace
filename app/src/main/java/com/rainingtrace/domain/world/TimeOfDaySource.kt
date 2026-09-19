package com.rainingtrace.domain.world

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 时段来源。
 *
 * 默认按真实时间推导（[timeOfDayOf]）；调试区可以固定成某个时段，
 * 用来验证"黎明/夜晚限定"的内容，而不必真的等到那个点。
 * 只覆盖时段判定，[WorldState.minuteOfDay] 仍是真实时间，
 * 所以 `BetweenMinutes` 这类精确到分钟的条件不受它影响。
 */
interface TimeOfDaySource {
    /** 非 null = 固定成这个时段；null = 按真实时间推导。 */
    val fixed: StateFlow<TimeOfDay?>
    fun setFixed(timeOfDay: TimeOfDay?)
}

class ManualTimeOfDaySource : TimeOfDaySource {

    private val _fixed = MutableStateFlow<TimeOfDay?>(null)
    override val fixed: StateFlow<TimeOfDay?> = _fixed.asStateFlow()

    override fun setFixed(timeOfDay: TimeOfDay?) {
        _fixed.value = timeOfDay
    }
}