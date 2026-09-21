package com.rainingtrace.domain.world

import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.core.time.WorldClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * 生产用的季节来源：**按节气推导** + 手动覆盖。
 *
 * 覆盖非 null 时优先（开发者模式手动切季节，验证季节限定内容）；
 * 覆盖为 null 时按 [seasonOf] 从当天日期推导。
 *
 * 与 [SystemWorldStateProvider] 同一省电口径：只在有人订阅时推进，
 * 一分钟一次 tick（季节一天最多变一次，够用；没人看就停）。
 */
class DerivedSeasonSource(
    private val clock: WorldClock,
    scope: CoroutineScope,
) : SeasonSource {

    private val _manualOverride = MutableStateFlow<Season?>(null)
    override val manualOverride: StateFlow<Season?> = _manualOverride.asStateFlow()

    override val season: StateFlow<Season?> = combine(_manualOverride, ticker()) { override, _ ->
        override ?: derivedSeason()
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT_MS),
        initialValue = derivedSeason(),
    )

    override fun setSeason(season: Season?) {
        _manualOverride.value = season
    }

    private fun derivedSeason(): Season = seasonOf(clock.now().atZone(WORLD_ZONE).toLocalDate())

    private fun ticker() = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val SUBSCRIBE_STOP_TIMEOUT_MS = 5 * 60_000L
    }
}
