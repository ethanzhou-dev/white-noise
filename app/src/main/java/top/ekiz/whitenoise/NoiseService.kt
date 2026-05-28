package top.ekiz.whitenoise

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import top.ekiz.whitenoise.audio.NoiseAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.cancel
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import javax.inject.Inject

@AndroidEntryPoint
class NoiseService : MediaSessionService() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
    val noiseProcessor = NoiseAudioProcessor()
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var audioManager: AudioManager
    private var modeListener: Any? = null
    private var isPausedByCall = false
    private var timerJob: Job? = null
    private var fadeJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(noiseProcessor))
                    .build()
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                false // handleAudioFocus = false (智能混音)
            )
            .build()

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand("START_SLEEP_TIMER", Bundle.EMPTY))
                    .add(SessionCommand("CANCEL_SLEEP_TIMER", Bundle.EMPTY))
                    .add(SessionCommand("PLAY_WITH_FADE", Bundle.EMPTY))
                    .add(SessionCommand("PAUSE_WITH_FADE", Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    "START_SLEEP_TIMER" -> {
                        val durationMs = args.getLong("DURATION_MS", 0L)
                        startTimer(durationMs)
                    }
                    "CANCEL_SLEEP_TIMER" -> {
                        cancelTimer()
                    }
                    "PLAY_WITH_FADE" -> {
                        fadeJob?.cancel()
                        noiseProcessor.fadeIn(1000L)
                        player.play()
                        isPausedByCall = false
                    }
                    "PAUSE_WITH_FADE" -> {
                        fadeJob?.cancel()
                        noiseProcessor.fadeOut(500L)
                        fadeJob = serviceScope.launch {
                            delay(500L)
                            player.pause()
                            noiseProcessor.fadeIn(100L)
                        }
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .build()

        // 365 days of silence in microseconds, practically infinite.
        // Some versions of SilenceMediaSource don't like C.TIME_UNSET, so we use a huge duration.
        val durationUs = 365L * 24 * 60 * 60 * 1000 * 1000
        val silenceSource = SilenceMediaSource(durationUs)
        
        player.setMediaSource(silenceSource)
        player.prepare()
        
        serviceScope.launch {
            settingsDataStore.volumeFlow.collect { volume ->
                noiseProcessor.volume = volume
            }
        }
        
        serviceScope.launch {
            settingsDataStore.noiseTypeFlow.collect { type ->
                noiseProcessor.noiseType = type
            }
        }
        
        serviceScope.launch {
            settingsDataStore.balanceFlow.collect { balance ->
                noiseProcessor.balance = balance
            }
        }

        serviceScope.launch {
            settingsDataStore.spatialAudioFlow.collect { enabled ->
                noiseProcessor.isSpatialAudioEnabled = enabled
            }
        }

        startCallMonitor()
    }

    private fun startTimer(durationMs: Long) {
        timerJob?.cancel()
        if (durationMs <= 0) return
        timerJob = serviceScope.launch {
            if (durationMs > 60000L) {
                delay(durationMs - 60000L) // Wait until last 60 seconds
                noiseProcessor.fadeOut(60000L)
                delay(60000L)
            } else {
                noiseProcessor.fadeOut(durationMs)
                delay(durationMs)
            }
            player.pause()
            noiseProcessor.fadeIn(100L) // reset fade for next time
            
            // Sync with ViewModel: clear timer state in DataStore
            settingsDataStore.saveTimerEndTime(0L)
            settingsDataStore.saveTimerRemaining(0L)
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        noiseProcessor.fadeIn(1000L) // reset fade in case it was fading out
    }

    private fun startCallMonitor() {
        audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        if (modeListener == null) {
            modeListener = AudioManager.OnModeChangedListener { mode ->
                val isCallActive = mode == AudioManager.MODE_IN_CALL || 
                                   mode == AudioManager.MODE_RINGTONE || 
                                   mode == AudioManager.MODE_IN_COMMUNICATION
                handleCallState(isCallActive)
            }
            audioManager.addOnModeChangedListener(mainExecutor, modeListener as AudioManager.OnModeChangedListener)
        }
    }

    private fun handleCallState(isCallActive: Boolean) {
        if (isCallActive) {
            if (player.isPlaying) {
                fadeJob?.cancel()
                noiseProcessor.fadeOut(500L)
                fadeJob = serviceScope.launch {
                    delay(500L)
                    player.pause()
                    isPausedByCall = true
                }
            }
        } else {
            if (isPausedByCall) {
                fadeJob?.cancel()
                noiseProcessor.fadeIn(1000L)
                player.play()
                isPausedByCall = false
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        if (modeListener != null) {
            audioManager.removeOnModeChangedListener(modeListener as AudioManager.OnModeChangedListener)
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
