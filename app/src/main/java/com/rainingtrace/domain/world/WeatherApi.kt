package com.rainingtrace.domain.world

import com.rainingtrace.domain.map.WorldCoordinate

/**
 * 真实天气数据源（外部 API 的抽象）。
 *
 * 契约只有一条，但很重要：**永不抛异常**——失败返回 `null`，
 * 由调用方决定保留上一次的好值。网络、超时、JSON 变形、未知天气码
 * 全都归到 `null`，绝不把异常漏进玩法层。
 */
interface WeatherApi {

    /** 取 [coordinate] 此刻的天气；拿不到就返回 null。 */
    suspend fun currentWeather(coordinate: WorldCoordinate): WeatherState?
}