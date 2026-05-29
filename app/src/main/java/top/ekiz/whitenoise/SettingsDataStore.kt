package top.ekiz.whitenoise

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        val VOLUME_KEY = floatPreferencesKey("volume")
        val NOISE_TYPE_KEY = stringPreferencesKey("noise_type")
        val SLEEP_TIMER_KEY = intPreferencesKey("sleep_timer")
        val TIMER_END_TIME_KEY = longPreferencesKey("timer_end_time")
        val TIMER_REMAINING_KEY = longPreferencesKey("timer_remaining")
        val TOTAL_TIMER_MILLIS_KEY = longPreferencesKey("total_timer_millis")
        val BALANCE_KEY = floatPreferencesKey("balance")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val SPATIAL_AUDIO_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("spatial_audio")
    }

    val volumeFlow: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[VOLUME_KEY] ?: 0.5f
        }.distinctUntilChanged()

    val noiseTypeFlow: Flow<NoiseType> = context.dataStore.data
        .map { preferences ->
            val typeString = preferences[NOISE_TYPE_KEY] ?: NoiseType.WHITE.name
            try {
                NoiseType.valueOf(typeString)
            } catch (e: Exception) {
                NoiseType.WHITE
            }
        }.distinctUntilChanged()

    val sleepTimerFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[SLEEP_TIMER_KEY] ?: 0 }.distinctUntilChanged()

    val timerEndTimeFlow: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[TIMER_END_TIME_KEY] ?: 0L }.distinctUntilChanged()

    val timerRemainingFlow: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[TIMER_REMAINING_KEY] ?: 0L }.distinctUntilChanged()

    val totalTimerMillisFlow: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[TOTAL_TIMER_MILLIS_KEY] ?: 0L }.distinctUntilChanged()

    val balanceFlow: Flow<Float> = context.dataStore.data
        .map { preferences -> preferences[BALANCE_KEY] ?: 0f }.distinctUntilChanged()

    val themeModeFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[THEME_MODE_KEY] ?: "System" }.distinctUntilChanged()

    val spatialAudioFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SPATIAL_AUDIO_KEY] ?: false }.distinctUntilChanged()

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

    suspend fun saveTimerEndTime(millis: Long) {
        context.dataStore.edit { preferences -> preferences[TIMER_END_TIME_KEY] = millis }
    }

    suspend fun saveTimerRemaining(millis: Long) {
        context.dataStore.edit { preferences -> preferences[TIMER_REMAINING_KEY] = millis }
    }

    suspend fun saveTotalTimerMillis(millis: Long) {
        context.dataStore.edit { preferences -> preferences[TOTAL_TIMER_MILLIS_KEY] = millis }
    }

    suspend fun saveBalance(balance: Float) {
        context.dataStore.edit { preferences -> preferences[BALANCE_KEY] = balance }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode }
    }

    suspend fun saveSpatialAudio(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SPATIAL_AUDIO_KEY] = enabled }
    }
}
