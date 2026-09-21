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
import com.rainingtrace.domain.settings.NpcClockOffset
import com.rainingtrace.domain.settings.NpcMessageSettings
import com.rainingtrace.domain.settings.ProactiveLevel
import com.rainingtrace.domain.settings.TrackingSettings
import com.rainingtrace.domain.settings.shownPlaceTypesFrom
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
            // 只认"隐藏集合"；首次升级时从旧的"显示集合"反算一次，之后不再写旧键。
            val hidden = prefs[KEY_HIDDEN_PLACE_TYPES]
                ?: prefs[KEY_SHOWN_PLACE_TYPES]?.let { stored ->
                    PlaceType.entries.map { it.name }.filterNot { it in stored }.toSet()
                }
                ?: emptySet()
            MapFilterSettings(
                shownPlaceTypes = shownPlaceTypesFrom(
                    hidden.mapNotNull { runCatching { PlaceType.valueOf(it) }.getOrNull() }.toSet(),
                ),
                showMemories = prefs[KEY_SHOW_MEMORIES] ?: true,
                memoryTimeFilter = prefs[KEY_MEMORY_TIME]
                    ?.let { runCatching { MemoryTimeFilter.valueOf(it) }.getOrNull() }
                    ?: MemoryTimeFilter.DEFAULT,
            )
        }

    override suspend fun currentMapFilter(): MapFilterSettings = mapFilter.first()

    override suspend fun setMapFilter(filter: MapFilterSettings) {
        context.settingsDataStore.edit { prefs ->
            // 存隐藏项：新增类型默认可见（见 shownPlaceTypesFrom 的说明）。
            prefs[KEY_HIDDEN_PLACE_TYPES] =
                PlaceType.entries.filterNot { it in filter.shownPlaceTypes }.map { it.name }.toSet()
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

    override val npcClockOffset: Flow<NpcClockOffset> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_NPC_CLOCK_OFFSET]?.let { NpcClockOffset.fromKey(it) } ?: NpcClockOffset.DEFAULT
        }

    override suspend fun currentNpcClockOffset(): NpcClockOffset = npcClockOffset.first()

    override suspend fun setNpcClockOffset(offset: NpcClockOffset) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_NPC_CLOCK_OFFSET] = offset.key
        }
    }

    override val npcMessages: Flow<NpcMessageSettings> =
        context.settingsDataStore.data.map { prefs ->
            NpcMessageSettings(
                proactiveLevel = prefs[KEY_NPC_PROACTIVE_LEVEL]
                    ?.let { ProactiveLevel.fromKey(it) }
                    ?: ProactiveLevel.DEFAULT,
                showAffection = prefs[KEY_NPC_SHOW_AFFECTION] ?: true,
            )
        }

    override suspend fun currentNpcMessages(): NpcMessageSettings = npcMessages.first()

    override suspend fun setNpcMessages(settings: NpcMessageSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_NPC_PROACTIVE_LEVEL] = settings.proactiveLevel.name
            prefs[KEY_NPC_SHOW_AFFECTION] = settings.showAffection
        }
    }

    private companion object {
        val KEY_GRID_LEVEL = stringPreferencesKey("grid_level")
        val KEY_LOCATION_MODE = stringPreferencesKey("location_mode")
        /** 旧键：存的是"显示集合"。只用于一次性反算，不再写入（见 mapFilter）。 */
        val KEY_SHOWN_PLACE_TYPES = stringSetPreferencesKey("shown_place_types")
        /** 新键：存"隐藏集合"，新增类型才会默认可见。 */
        val KEY_HIDDEN_PLACE_TYPES = stringSetPreferencesKey("hidden_place_types")
        val KEY_SHOW_MEMORIES = booleanPreferencesKey("show_memories")
        val KEY_MEMORY_TIME = stringPreferencesKey("memory_time")
        val KEY_TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val KEY_BG_INTERVAL = stringPreferencesKey("tracking_bg_interval")
        val KEY_DAYTIME_ONLY = booleanPreferencesKey("tracking_daytime_only")
        val KEY_DAY_WINDOW = stringPreferencesKey("tracking_day_window")
        val KEY_FOG_WATERMARK = longPreferencesKey("fog_watermark_ms")
        val KEY_NPC_CLOCK_OFFSET = stringPreferencesKey("npc_clock_offset")
        val KEY_NPC_PROACTIVE_LEVEL = stringPreferencesKey("npc_proactive_level")
        val KEY_NPC_SHOW_AFFECTION = booleanPreferencesKey("npc_show_affection")
    }
}
