package top.ekiz.whitenoise

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class TimerEvent {
    START_FADE_OUT,
    TIMER_FINISHED,
    TIMER_CANCELLED
}

@Singleton
class TimerManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    private val _activeRemainingMillis = MutableStateFlow(0L)
    val activeRemainingMillis = _activeRemainingMillis.asStateFlow()

    private val _totalTimerMillis = MutableStateFlow(0L)
    val totalTimerMillis = _totalTimerMillis.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    private val _timerEvents = MutableSharedFlow<TimerEvent>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val timerEvents = _timerEvents.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    init {
        // Restore timer state on startup
        scope.launch {
            val total = settingsDataStore.totalTimerMillisFlow.first()
            if (total > 0L) {
                _totalTimerMillis.value = total
            }
            
            val endTime = settingsDataStore.timerEndTimeFlow.first()
            if (endTime > 0L) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining > 0) {
                    // Timer was running and hasn't expired
                    startTicker(remaining, endTime)
                } else {
                    // Timer expired while app was dead
                    settingsDataStore.saveTimerEndTime(0L)
                    settingsDataStore.saveTimerRemaining(0L)
                    settingsDataStore.saveTotalTimerMillis(0L)
                    _totalTimerMillis.value = 0L
                }
            } else {
                // Check if there's a paused timer
                val remaining = settingsDataStore.timerRemainingFlow.first()
                if (remaining > 0L) {
                    _activeRemainingMillis.value = remaining
                } else {
                    _totalTimerMillis.value = 0L
                }
            }
        }
    }

    fun startTimer(durationMs: Long, isResume: Boolean = false) {
        if (durationMs <= 0) return
        
        scope.launch {
            if (!isResume || _totalTimerMillis.value <= 0L) {
                _totalTimerMillis.value = durationMs
                settingsDataStore.saveTotalTimerMillis(durationMs)
            }
            val endTime = System.currentTimeMillis() + durationMs
            
            // Save Intent to DataStore (Persistence)
            settingsDataStore.saveTimerRemaining(0L)
            settingsDataStore.saveTimerEndTime(endTime)
            
            startTicker(durationMs, endTime)
        }
    }

    fun pauseTimer() {
        tickerJob?.cancel()
        
        scope.launch {
            val remaining = _activeRemainingMillis.value
            _isTimerRunning.value = false
            
            // Save Intent to DataStore (Persistence)
            settingsDataStore.saveTimerEndTime(0L)
            if (remaining > 0) {
                settingsDataStore.saveTimerRemaining(remaining)
            }
            
            _timerEvents.tryEmit(TimerEvent.TIMER_CANCELLED)
        }
    }

    fun cancelTimer() {
        tickerJob?.cancel()
        
        scope.launch {
            _activeRemainingMillis.value = 0L
            _totalTimerMillis.value = 0L
            _isTimerRunning.value = false
            
            // Save Intent to DataStore (Persistence)
            settingsDataStore.saveTimerEndTime(0L)
            settingsDataStore.saveTimerRemaining(0L)
            settingsDataStore.saveTotalTimerMillis(0L)
            
            _timerEvents.tryEmit(TimerEvent.TIMER_CANCELLED)
        }
    }

    private fun startTicker(durationMs: Long, endTime: Long) {
        tickerJob?.cancel()
        _isTimerRunning.value = true
        _activeRemainingMillis.value = durationMs
        
        tickerJob = scope.launch {
            var fadeTriggered = false
            
            while (true) {
                val remaining = endTime - System.currentTimeMillis()
                
                // Trigger fade out at exactly 60 seconds (or immediately if started < 60s)
                if (remaining <= 60000L && !fadeTriggered) {
                    fadeTriggered = true
                    _timerEvents.tryEmit(TimerEvent.START_FADE_OUT)
                }

                if (remaining > 0) {
                    _activeRemainingMillis.value = remaining
                } else {
                    // Timer finished
                    _activeRemainingMillis.value = 0L
                    _totalTimerMillis.value = 0L
                    _isTimerRunning.value = false
                    
                    settingsDataStore.saveTimerEndTime(0L)
                    settingsDataStore.saveTimerRemaining(0L)
                    settingsDataStore.saveTotalTimerMillis(0L)
                    
                    _timerEvents.tryEmit(TimerEvent.TIMER_FINISHED)
                    break
                }
                delay(1000)
            }
        }
    }
}
