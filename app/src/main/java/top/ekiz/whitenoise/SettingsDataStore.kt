package top.ekiz.whitenoise

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val VOLUME_KEY = floatPreferencesKey("volume")
        val NOISE_TYPE_KEY = stringPreferencesKey("noise_type")
        val SLEEP_TIMER_KEY = intPreferencesKey("sleep_timer")
        val BALANCE_KEY = floatPreferencesKey("balance")
        val STEREO_WIDTH_KEY = floatPreferencesKey("stereo_width")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
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

    val sleepTimerFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[SLEEP_TIMER_KEY] ?: 0 }

    val balanceFlow: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[BALANCE_KEY] ?: 0f }

    val stereoWidthFlow: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[STEREO_WIDTH_KEY] ?: 1f }

    val themeModeFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[THEME_MODE_KEY] ?: "System" }

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

    suspend fun saveSleepTimer(minutes: Int) {
        context.dataStore.edit { preferences -> preferences[SLEEP_TIMER_KEY] = minutes }
    }

    suspend fun saveBalance(balance: Float) {
        context.dataStore.edit { preferences -> preferences[BALANCE_KEY] = balance }
    }

    suspend fun saveStereoWidth(width: Float) {
        context.dataStore.edit { preferences -> preferences[STEREO_WIDTH_KEY] = width }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode }
    }
}
