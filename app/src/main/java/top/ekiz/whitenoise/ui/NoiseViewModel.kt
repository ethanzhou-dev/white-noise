package top.ekiz.whitenoise.ui

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.ekiz.whitenoise.NoiseType
import top.ekiz.whitenoise.SettingsDataStore
import top.ekiz.whitenoise.TimerManager
import javax.inject.Inject

data class NoiseUiState(
    val volume: Float = 0.5f,
    val balance: Float = 0f,
    val noiseType: NoiseType = NoiseType.WHITE,
    val sleepTimerMinutes: Int = 0,
    val themeMode: String = "System",
    val isSpatialAudioEnabled: Boolean = false,
    val isPlaying: Boolean = false,
    val activeRemainingMillis: Long = 0L,
    val totalTimerMillis: Long = 0L,
    val isTimerRunning: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class NoiseViewModel @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val timerManager: TimerManager
) : ViewModel() {

    private var mediaController: MediaController? = null

    // Local mutable state for fast-updating UI fields (like current playback state)
    private val _isPlaying = MutableStateFlow(false)

    private val settingsFlow = combine(
        dataStore.volumeFlow,
        dataStore.balanceFlow,
        dataStore.noiseTypeFlow,
        dataStore.sleepTimerFlow,
        dataStore.themeModeFlow,
        dataStore.spatialAudioFlow
    ) { values ->
        NoiseUiState(
            volume = values[0] as Float,
            balance = values[1] as Float,
            noiseType = values[2] as NoiseType,
            sleepTimerMinutes = values[3] as Int,
            themeMode = values[4] as String,
            isSpatialAudioEnabled = values[5] as Boolean
        )
    }

    private val runtimeFlow = combine(
        _isPlaying,
        timerManager.activeRemainingMillis,
        timerManager.totalTimerMillis,
        timerManager.isTimerRunning
    ) { isPlaying, remaining, total, isTimerRunning ->
        arrayOf(isPlaying, remaining, total, isTimerRunning)
    }

    val uiState: StateFlow<NoiseUiState> = combine(
        settingsFlow,
        runtimeFlow
    ) { settings, runtime ->
        settings.copy(
            isPlaying = runtime[0] as Boolean,
            activeRemainingMillis = runtime[1] as Long,
            totalTimerMillis = runtime[2] as Long,
            isTimerRunning = runtime[3] as Boolean,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NoiseUiState(isLoading = true)
    )

    fun setMediaController(controller: MediaController?) {
        mediaController = controller
        updatePlaybackState()
    }

    fun updatePlaybackState() {
        _isPlaying.value = mediaController?.isPlaying == true
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.sendCustomCommand(SessionCommand("PAUSE_WITH_FADE", Bundle.EMPTY), Bundle.EMPTY)
        } else {
            controller.sendCustomCommand(SessionCommand("PLAY_WITH_FADE", Bundle.EMPTY), Bundle.EMPTY)
        }
    }

    fun setVolume(volume: Float) {
        viewModelScope.launch { dataStore.saveVolume(volume) }
    }

    fun setBalance(balance: Float) {
        viewModelScope.launch { dataStore.saveBalance(balance) }
    }

    fun setNoiseType(type: NoiseType) {
        viewModelScope.launch { dataStore.saveNoiseType(type) }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        viewModelScope.launch {
            dataStore.saveSleepTimer(minutes)
            if (timerManager.isTimerRunning.value) {
                // Auto-restart timer with new value if running
                val totalMillis = minutes * 60000L
                if (totalMillis > 0) {
                    timerManager.startTimer(totalMillis, isResume = false)
                } else {
                    timerManager.pauseTimer()
                }
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { dataStore.saveThemeMode(mode) }
    }

    fun setSpatialAudioEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.saveSpatialAudio(enabled) }
    }

    fun startTimer() {
        val uiStateVal = uiState.value
        val resumeMillis = timerManager.activeRemainingMillis.value
        val isResume = resumeMillis > 0L

        val durationMillis = if (isResume) {
            resumeMillis
        } else {
            uiStateVal.sleepTimerMinutes * 60000L
        }
        
        timerManager.startTimer(durationMillis, isResume)
    }

    fun pauseTimer() {
        timerManager.pauseTimer()
    }

    fun cancelTimer() {
        timerManager.cancelTimer()
    }
}
