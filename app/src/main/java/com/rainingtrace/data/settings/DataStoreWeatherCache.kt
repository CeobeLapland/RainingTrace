package com.rainingtrace.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rainingtrace.domain.world.WeatherCache
import com.rainingtrace.domain.world.WeatherCacheEntry
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import kotlinx.coroutines.flow.first

/**
 * 天气缓存落在**同一个** `rt_settings` 文件里。
 *
 * 注意：这里的 `settingsDataStore` 是 `internal` 共享的委托——同一份文件不能有第二个
 * `preferencesDataStore(...)`，否则会在运行期抛 "multiple DataStores active for the same file"。
 *
 * 缺字段或枚举值不认（比如以后删过某个 [WeatherKind]）时返回 null，
 * 而不是编一个默认天气出来。
 */
class DataStoreWeatherCache(
    private val context: Context,
) : WeatherCache {

    override suspend fun load(): WeatherCacheEntry? {
        val prefs = context.settingsDataStore.data.first()
        val kind = prefs[KEY_KIND]?.let { raw ->
            WeatherKind.entries.firstOrNull { it.name == raw }
        } ?: return null
        val fetchedAt = prefs[KEY_FETCHED_AT_MS] ?: return null
        return WeatherCacheEntry(
            state = WeatherState(
                kind = kind,
                // 落盘的值也要过一遍钳位：老安装里可能存着越界的数。
                humidity = (prefs[KEY_HUMIDITY] ?: 0.5).coerceIn(0.0, 1.0),
                temperatureCelsius = prefs[KEY_TEMPERATURE_C] ?: 20.0,
            ),
            fetchedAtEpochMs = fetchedAt,
        )
    }

    override suspend fun save(entry: WeatherCacheEntry) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_KIND] = entry.state.kind.name
            prefs[KEY_HUMIDITY] = entry.state.humidity
            prefs[KEY_TEMPERATURE_C] = entry.state.temperatureCelsius
            prefs[KEY_FETCHED_AT_MS] = entry.fetchedAtEpochMs
        }
    }

    private companion object {
        val KEY_KIND = stringPreferencesKey("weather_kind")
        val KEY_HUMIDITY = doublePreferencesKey("weather_humidity")
        val KEY_TEMPERATURE_C = doublePreferencesKey("weather_temp_c")
        val KEY_FETCHED_AT_MS = longPreferencesKey("weather_fetched_at_ms")
    }
}