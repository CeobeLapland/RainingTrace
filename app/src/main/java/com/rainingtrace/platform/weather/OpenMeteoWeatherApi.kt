package com.rainingtrace.platform.weather

import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.WeatherApi
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.weatherKindOfWmoCode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Open-Meteo 天气适配器。
 *
 * 选它的理由：**非商用免费、无需 API key**、调用上限 10000 次/日
 * （本项目 15 分钟拉一次 = 96 次/日），数据的许可证是 CC BY 4.0（**要求署名**，
 * 设置页里有那一行）。不需要在 APK 里塞任何密钥，也就没有泄露面。
 *
 * 解析用 `bodyAsText()` + 本地 [Json]，**不引入 ktor 的 content-negotiation**：
 * 这一点点胶水不值得多一个依赖（ktor-client-core 是随 okhttp 引擎进来的）。
 *
 * 全程 `runCatching`：网络、超时、变形 JSON、未知天气码一律变成 `null`，
 * 由 `RemoteWeatherSource` 保留上一次的好值。
 */
class OpenMeteoWeatherApi(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : WeatherApi {

    override suspend fun currentWeather(coordinate: WorldCoordinate): WeatherState? = runCatching {
        // 不带 timezone=auto：时段由本地的 WorldClock 推导，响应里那段我们用不上。
        val url = "$baseUrl?latitude=${coordinate.latDegrees}" +
            "&longitude=${coordinate.lngDegrees}&current=$CURRENT_FIELDS"
        val body = client.get(url).bodyAsText()
        OpenMeteoFormat.decodeFromString<OpenMeteoResponse>(body).current?.toWeatherState()
    }.getOrNull()

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com/v1/forecast"
        const val CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,weather_code"

        val OpenMeteoFormat = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class OpenMeteoResponse(val current: OpenMeteoCurrent? = null)

@Serializable
private data class OpenMeteoCurrent(
    @SerialName("temperature_2m") val temperatureCelsius: Double? = null,
    @SerialName("relative_humidity_2m") val relativeHumidityPercent: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
)

private fun OpenMeteoCurrent.toWeatherState(): WeatherState? {
    // 未知天气码 = 不知道，返回 null 让上层保留上次好值，而不是猜一个。
    val kind = weatherCode?.let(::weatherKindOfWmoCode) ?: return null
    return WeatherState(
        kind = kind,
        // API 给的是百分比（72），领域要 0..1。**必须钳位**：台站偶发 101 这类值，
        // 而 WeatherState.init 有 `require(humidity in 0.0..1.0)`——
        // 不钳位就会在这个"永不抛异常"的解析路径里抛出。
        humidity = ((relativeHumidityPercent ?: DEFAULT_HUMIDITY_PERCENT) / 100.0)
            .coerceIn(0.0, 1.0),
        temperatureCelsius = temperatureCelsius ?: DEFAULT_TEMPERATURE_CELSIUS,
    )
}

/** 缺字段时的兜底：只影响显示，不参与任何玩法条件。 */
private const val DEFAULT_HUMIDITY_PERCENT = 50.0
private const val DEFAULT_TEMPERATURE_CELSIUS = 20.0