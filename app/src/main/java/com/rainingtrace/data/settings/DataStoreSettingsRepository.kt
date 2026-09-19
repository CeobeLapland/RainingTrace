package com.rainingtrace.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rainingtrace.domain.map.GridLevel
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.BackgroundInterval
import com.rainingtrace.domain.settings.DayWindow
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.settings.MapFilterSettings
import com.rainingtrace.domain.settings.MemoryTimeFilter
import com.rainingtrace.domain.settings.TrackingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "rt_settings")

class DataStoreSettingsRepository(
    private val context: Context,
) : AppSettingsRepository {

    override val gridLevel: Flow<GridLevel> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_GRID_LEVEL]?.let { GridLevel.fromKey(it) } ?: GridLevel.DEFAULT
        }

    override suspend fun currentGridLevel(): GridLevel = gridLevel.first()

    override suspend fun setGridLevel(level: GridLevel) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_GRID_LEVEL] = level.key
        }
    }

    override val locationMode: Flow<LocationMode> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_LOCATION_MODE]
                ?.let { runCatching { LocationMode.valueOf(it) }.getOrNull() }
                ?: LocationMode.DEFAULT
        }

    override suspend fun currentLocationMode(): LocationMode = locationMode.first()

    override suspend fun setLocationMode(mode: LocationMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LOCATION_MODE] = mode.name
        }
    }

    override val mapFilter: Flow<MapFilterSettings> =
        context.settingsDataStore.data.map { prefs ->
            val types = prefs[KEY_SHOWN_PLACE_TYPES]
                ?.mapNotNull { runCatching { PlaceType.valueOf(it) }.getOrNull() }
                ?.toSet()
            MapFilterSettings(
                // 未存过（old）或解析为空时，回退到"全部显示"，避免首次进入把所有地点藏掉。
                shownPlaceTypes = types ?: PlaceType.entries.toSet(),
                showMemories = prefs[KEY_SHOW_MEMORIES] ?: true,
                memoryTimeFilter = prefs[KEY_MEMORY_TIME]
                    ?.let { runCatching { MemoryTimeFilter.valueOf(it) }.getOrNull() }
                    ?: MemoryTimeFilter.DEFAULT,
            )
        }

    override suspend fun currentMapFilter(): MapFilterSettings = mapFilter.first()

    override suspend fun setMapFilter(filter: MapFilterSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SHOWN_PLACE_TYPES] = filter.shownPlaceTypes.map { it.name }.toSet()
            prefs[KEY_SHOW_MEMORIES] = filter.showMemories
            prefs[KEY_MEMORY_TIME] = filter.memoryTimeFilter.name
        }
    }

    override val tracking: Flow<TrackingSettings> =
        context.settingsDataStore.data.map { prefs ->
            TrackingSettings(
                enabled = prefs[KEY_TRACKING_ENABLED] ?: false,
                backgroundInterval = prefs[KEY_BG_INTERVAL]
                    ?.let { runCatching { BackgroundInterval.valueOf(it) }.getOrNull() }
                    ?: BackgroundInterval.DEFAULT,
                daytimeOnly = prefs[KEY_DAYTIME_ONLY] ?: true,
                dayWindow = prefs[KEY_DAY_WINDOW]
                    ?.let { runCatching { DayWindow.valueOf(it) }.getOrNull() }
                    ?: DayWindow.DEFAULT,
            )
        }

    override suspend fun currentTracking(): TrackingSettings = tracking.first()

    override suspend fun setTracking(settings: TrackingSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_TRACKING_ENABLED] = settings.enabled
            prefs[KEY_BG_INTERVAL] = settings.backgroundInterval.name
            prefs[KEY_DAYTIME_ONLY] = settings.daytimeOnly
            prefs[KEY_DAY_WINDOW] = settings.dayWindow.name
        }
    }

    override suspend fun fogWatermarkMs(): Long? =
        context.settingsDataStore.data.map { it[KEY_FOG_WATERMARK] }.first()

    override suspend fun setFogWatermarkMs(epochMs: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_FOG_WATERMARK] = epochMs
        }
    }

    private companion object {
        val KEY_GRID_LEVEL = stringPreferencesKey("grid_level")
        val KEY_LOCATION_MODE = stringPreferencesKey("location_mode")
        val KEY_SHOWN_PLACE_TYPES = stringSetPreferencesKey("shown_place_types")
        val KEY_SHOW_MEMORIES = booleanPreferencesKey("show_memories")
        val KEY_MEMORY_TIME = stringPreferencesKey("memory_time")
        val KEY_TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val KEY_BG_INTERVAL = stringPreferencesKey("tracking_bg_interval")
        val KEY_DAYTIME_ONLY = booleanPreferencesKey("tracking_daytime_only")
        val KEY_DAY_WINDOW = stringPreferencesKey("tracking_day_window")
        val KEY_FOG_WATERMARK = longPreferencesKey("fog_watermark_ms")
    }
}
