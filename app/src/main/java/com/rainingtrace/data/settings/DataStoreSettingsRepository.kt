package com.rainingtrace.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rainingtrace.domain.map.GridLevel
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.LocationMode
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

    private companion object {
        val KEY_GRID_LEVEL = stringPreferencesKey("grid_level")
        val KEY_LOCATION_MODE = stringPreferencesKey("location_mode")
    }
}
