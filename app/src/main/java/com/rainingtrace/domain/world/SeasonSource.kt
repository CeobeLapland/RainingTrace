package com.rainingtrace.domain.world

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 季节来源。
 *
 * 生产实现是 [DerivedSeasonSource]：**按节气推导**（立春/立夏/立秋/立冬），
 * 同时保留一个手动覆盖值给开发者模式（设置页调试区），覆盖非 null 时优先。
 *
 * [WorldCondition.SeasonIn] 在季节为 null 时一律不满足——不给"猜"的机会；
 * 接上推导后正常游玩中季节不再为 null，所以季节限定的产出会真实触发。
 */
interface SeasonSource {
    /** 当前生效的季节（覆盖优先，否则推导）。 */
    val season: StateFlow<Season?>

    /** 手动覆盖值；null = 按节气推导。UI 用它渲染"自动"行。 */
    val manualOverride: StateFlow<Season?>

    /** 传 null = 回到按节气推导。 */
    fun setSeason(season: Season?)
}

/** 手动实现：测试与开发者模式用（[season] 就等于覆盖值，没有推导）。 */
class ManualSeasonSource(initial: Season? = null) : SeasonSource {

    private val _season = MutableStateFlow(initial)
    override val season: StateFlow<Season?> = _season.asStateFlow()

    override val manualOverride: StateFlow<Season?> = _season.asStateFlow()

    override fun setSeason(season: Season?) {
        _season.value = season
    }
}
