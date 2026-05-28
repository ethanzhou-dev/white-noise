package top.ekiz.whitenoise.ui

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.ekiz.whitenoise.NoiseType
import top.ekiz.whitenoise.SettingsDataStore
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
    private val dataStore: SettingsDataStore
) : ViewModel() {

    companion object {
        const val CMD_START_TIMER = "START_SLEEP_TIMER"
        const val CMD_CANCEL_TIMER = "CANCEL_SLEEP_TIMER"
        const val EXTRA_DURATION_MS = "DURATION_MS"
    }

    private var mediaController: MediaController? = null

    // Local mutable state for fast-updating UI fields (like current playback state and timer ticker)
    private val _isPlaying = MutableStateFlow(false)
    private val _activeRemainingMillis = MutableStateFlow(0L)
    private val _isTimerRunning = MutableStateFlow(false)
    private val _totalTimerMillis = MutableStateFlow(0L)

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
        _activeRemainingMillis,
        _totalTimerMillis,
        _isTimerRunning
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

    init {
        // Ticker for UI
        viewModelScope.launch {
            dataStore.timerEndTimeFlow.collectLatest { endTime ->
                if (endTime > 0L) {
                    _isTimerRunning.value = true
                    while (true) {
                        val remaining = endTime - System.currentTimeMillis()
                        if (remaining > 0) {
                            _activeRemainingMillis.value = remaining
                        } else {
                            // Timer expired naturally. 
                            // The actual pause and fade out are handled natively by NoiseService.
                            // We clear the local UI state and DataStore so the UI is immediately synced.
                            _activeRemainingMillis.value = 0L
                            _isTimerRunning.value = false
                            _totalTimerMillis.value = 0L
                            dataStore.saveTimerEndTime(0L)
                            dataStore.saveTimerRemaining(0L)
                            break
                        }
                        delay(1000)
                    }
                } else {
                    _isTimerRunning.value = false
                    // One-shot read of paused remaining time (not a nested collect)
                    val remaining = dataStore.timerRemainingFlow.first()
                    _activeRemainingMillis.value = remaining
                }
            }
        }
    }

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
        // updatePlaybackState() will be called when ExoPlayer's state actually changes
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
            if (_isTimerRunning.value) {
                // Auto-restart timer with new value if running
                val totalMillis = minutes * 60000L
                if (totalMillis > 0) {
                    _totalTimerMillis.value = totalMillis
                    dataStore.saveTimerRemaining(0L)
                    dataStore.saveTimerEndTime(System.currentTimeMillis() + totalMillis)
                    val bundle = Bundle().apply { putLong(EXTRA_DURATION_MS, totalMillis) }
                    mediaController?.sendCustomCommand(SessionCommand(CMD_START_TIMER, Bundle.EMPTY), bundle)
                } else {
                    pauseTimer()
                    dataStore.saveTimerRemaining(0L)
                    _activeRemainingMillis.value = 0L
                }
            } else {
                dataStore.saveTimerRemaining(0L)
                _activeRemainingMillis.value = 0L
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
        viewModelScope.launch {
            val uiStateVal = uiState.value
            val resumeMillis = uiStateVal.activeRemainingMillis
            val isResume = resumeMillis > 0L

            val durationMillis = if (isResume) {
                resumeMillis
            } else {
                uiStateVal.sleepTimerMinutes * 60000L
            }
            
            if (durationMillis <= 0) return@launch

            // On fresh start, set totalTimerMillis to the full duration.
            // On resume, keep the existing total so the progress bar continues from the correct ratio.
            if (!isResume || _totalTimerMillis.value <= 0L) {
                _totalTimerMillis.value = durationMillis
            }
            
            // 1. Save to DataStore for UI persistence across restarts
            dataStore.saveTimerRemaining(0L)
            dataStore.saveTimerEndTime(System.currentTimeMillis() + durationMillis)
            
            // 2. Send Custom Command to Service for execution
            val bundle = Bundle().apply { putLong(EXTRA_DURATION_MS, durationMillis) }
            mediaController?.sendCustomCommand(SessionCommand(CMD_START_TIMER, Bundle.EMPTY), bundle)
        }
    }

    fun pauseTimer() {
        viewModelScope.launch {
            val remaining = _activeRemainingMillis.value
            
            // 1. Update DataStore UI state
            dataStore.saveTimerRemaining(remaining)
            dataStore.saveTimerEndTime(0L)
            
            // 2. Cancel Service execution
            mediaController?.sendCustomCommand(SessionCommand(CMD_CANCEL_TIMER, Bundle.EMPTY), Bundle.EMPTY)
        }
    }

    fun cancelTimer() {
        viewModelScope.launch {
            // Fully reset all timer state
            _activeRemainingMillis.value = 0L
            _totalTimerMillis.value = 0L
            _isTimerRunning.value = false
            
            dataStore.saveTimerRemaining(0L)
            dataStore.saveTimerEndTime(0L)
            
            mediaController?.sendCustomCommand(SessionCommand(CMD_CANCEL_TIMER, Bundle.EMPTY), Bundle.EMPTY)
        }
    }
}
