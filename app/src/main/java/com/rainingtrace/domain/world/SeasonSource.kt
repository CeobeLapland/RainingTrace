package com.rainingtrace.domain.world

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 季节来源。
 *
 * 划分口径（节气 vs 月份）还没定，所以**不做推导**：当前实现是手动设定，
 * 由设置页的调试区切换，好让季节条件能被真机验证。
 * 将来接日历规则或服务端下发时换一个实现即可，[WorldCondition] 与玩法不用改。
 */
interface SeasonSource {
    /** null = 未确定；此时季节条件一律不满足（见 SeasonIn）。 */
    val season: StateFlow<Season?>
    fun setSeason(season: Season?)
}

class ManualSeasonSource(initial: Season? = null) : SeasonSource {

    private val _season = MutableStateFlow(initial)
    override val season: StateFlow<Season?> = _season.asStateFlow()

    override fun setSeason(season: Season?) {
        _season.value = season
    }
}