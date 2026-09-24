package com.rainingtrace.domain.world

import kotlinx.coroutines.flow.StateFlow

/**
 * 天气的"真实来源 + 手动覆盖"（**与 [SeasonSource] 同构**，见 `DerivedSeasonSource`）。
 *
 * 覆盖非 null 时优先；[setOverride] 传 null 回到真实来源。
 * 这样"接真实天气"和"保留调试切换"两件事不冲突：调试切一下立刻生效，
 * 点回"自动"又跟着真实值走。
 */
interface WeatherSource : WeatherProvider {

    /** 手动覆盖值；null = 跟随真实来源。UI 用它判断"自动"那一行是否选中。 */
    val manualOverride: StateFlow<WeatherState?>

    /** 真实来源的状态（上次成功时间 / 是否在重试）；纯手动的实现恒为 [WeatherStatus.NOT_APPLICABLE]。 */
    val status: StateFlow<WeatherStatus>

    fun setOverride(state: WeatherState?)
}

/**
 * 真实天气来源的状态。
 *
 * "还没拿到"刻意**不**用一个新的 [WeatherKind] 表示：那会污染
 * `WeatherKind.label()` 的穷尽 when、`weatherPreset`，以及**内容 JSON 的合法天气值列表**
 * （`ContentJson.parseWeatherKinds` 会把它当成可填的天气卖给人）。放在这里最干净。
 */
data class WeatherStatus(
    /** 上次成功拿到真实天气的时刻；null = 从未成功（或本实现不适用）。 */
    val lastUpdatedAtEpochMs: Long?,
    /** true = 上一次尝试失败了，正在等下一次重试。 */
    val isRetrying: Boolean,
) {
    companion object {
        /** 纯手动/Fake 实现：没有"真实来源"这回事。 */
        val NOT_APPLICABLE = WeatherStatus(lastUpdatedAtEpochMs = null, isRetrying = false)
    }
}

/** 便捷：按预设值覆盖（手动调试区一行一个天气）。 */
fun WeatherSource.setKind(kind: WeatherKind) {
    setOverride(weatherPreset(kind))
}