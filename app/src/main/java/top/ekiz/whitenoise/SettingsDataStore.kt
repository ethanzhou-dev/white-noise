package top.ekiz.whitenoise

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val VOLUME_KEY = floatPreferencesKey("volume")
        val NOISE_TYPE_KEY = stringPreferencesKey("noise_type")
    }

    val volumeFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[VOLUME_KEY] ?: 0.5f
        }

    val noiseTypeFlow: Flow<NoiseType> = context.dataStore.data
        .map { preferences ->
            val typeString = preferences[NOISE_TYPE_KEY] ?: NoiseType.WHITE.name
            try {
                NoiseType.valueOf(typeString)
            } catch (e: Exception) {
                NoiseType.WHITE
            }
        }

    suspend fun saveVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[VOLUME_KEY] = volume
        }
    }

    suspend fun saveNoiseType(noiseType: NoiseType) {
        context.dataStore.edit { preferences ->
            preferences[NOISE_TYPE_KEY] = noiseType.name
        }
    }
}
