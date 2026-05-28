package top.ekiz.whitenoise.ui

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val isPlaying: Boolean = false,
    val activeRemainingMillis: Long = 0L,
    val totalTimerMillis: Long = 0L,
    val isTimerRunning: Boolean = false
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
        dataStore.themeModeFlow
    ) { volume, balance, type, timerMinutes, theme ->
        NoiseUiState(
            volume = volume,
            balance = balance,
            noiseType = type,
            sleepTimerMinutes = timerMinutes,
            themeMode = theme
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
            isTimerRunning = runtime[3] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NoiseUiState()
    )

    init {
        // Ticker for UI
        viewModelScope.launch {
            dataStore.timerEndTimeFlow.collect { endTime ->
                if (endTime > 0L) {
                    _isTimerRunning.value = true
                    while (true) {
                        val remaining = endTime - System.currentTimeMillis()
                        if (remaining > 0) {
                            _activeRemainingMillis.value = remaining
                        } else {
                            _activeRemainingMillis.value = 0L
                            _isTimerRunning.value = false
                            dataStore.saveTimerEndTime(0L)
                            break
                        }
                        delay(1000)
                    }
                } else {
                    _isTimerRunning.value = false
                    // When not running, load the paused remaining time
                    dataStore.timerRemainingFlow.collect { remaining ->
                        _activeRemainingMillis.value = remaining
                    }
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
            controller.pause()
        } else {
            controller.play()
        }
        updatePlaybackState()
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
        viewModelScope.launch { dataStore.saveSleepTimer(minutes) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { dataStore.saveThemeMode(mode) }
    }

    fun startTimer() {
        viewModelScope.launch {
            val uiStateVal = uiState.value
            val totalMillis = if (uiStateVal.activeRemainingMillis > 0L) {
                uiStateVal.activeRemainingMillis
            } else {
                uiStateVal.sleepTimerMinutes * 60000L
            }
            
            if (totalMillis <= 0) return@launch

            _totalTimerMillis.value = totalMillis
            
            // 1. Save to DataStore for UI persistence across restarts
            dataStore.saveTimerRemaining(0L)
            dataStore.saveTimerEndTime(System.currentTimeMillis() + totalMillis)
            
            // 2. Send Custom Command to Service for execution
            val bundle = Bundle().apply { putLong(EXTRA_DURATION_MS, totalMillis) }
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
}
