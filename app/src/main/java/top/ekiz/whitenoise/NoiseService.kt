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
import kotlinx.coroutines.flow.first
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
                        fadeJob = serviceScope.launch {
                            player.volume = 0f
                            player.play()
                            isPausedByCall = false
                            animatePlayerVolume(0f, 1f, 1000L)
                        }
                    }
                    "PAUSE_WITH_FADE" -> {
                        fadeJob?.cancel()
                        fadeJob = serviceScope.launch {
                            animatePlayerVolume(1f, 0f, 500L)
                            player.pause()
                            player.seekTo(0)
                            player.volume = 1f
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

        serviceScope.launch {
            // Wait for initial values from DataStore BEFORE preparing the player
            // This prevents ExoPlayer from pre-buffering the default WHITE noise
            val initialVolume = settingsDataStore.volumeFlow.first()
            val initialNoiseType = settingsDataStore.noiseTypeFlow.first()
            val initialBalance = settingsDataStore.balanceFlow.first()
            val initialSpatial = settingsDataStore.spatialAudioFlow.first()

            noiseProcessor.volume = initialVolume
            noiseProcessor.initialize(initialNoiseType)
            noiseProcessor.balance = initialBalance
            noiseProcessor.isSpatialAudioEnabled = initialSpatial

            // Now that the processor is correctly configured, prepare the player
            player.setMediaSource(silenceSource)
            player.prepare()

            // Start collecting ongoing updates
            launch { settingsDataStore.volumeFlow.collect { noiseProcessor.volume = it } }
            launch { settingsDataStore.noiseTypeFlow.collect { noiseProcessor.noiseType = it } }
            launch { settingsDataStore.balanceFlow.collect { noiseProcessor.balance = it } }
            launch { settingsDataStore.spatialAudioFlow.collect { noiseProcessor.isSpatialAudioEnabled = it } }
        }

        serviceScope.launch {
            settingsDataStore.timerEndTimeFlow.collectLatest { endTime ->
                if (endTime > 0L) {
                    val remaining = endTime - System.currentTimeMillis()
                    if (remaining > 0) {
                        // Restore timerJob if we don't have an active one
                        if (timerJob == null || timerJob?.isActive != true) {
                            startTimer(remaining)
                        }
                    }
                }
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
                animatePlayerVolume(1f, 0f, 60000L)
            } else {
                animatePlayerVolume(1f, 0f, durationMs)
            }
            player.pause()
            player.seekTo(0)
            player.volume = 1f
            
            // Sync with ViewModel: clear timer state in DataStore
            settingsDataStore.saveTimerEndTime(0L)
            settingsDataStore.saveTimerRemaining(0L)
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        fadeJob?.cancel()
        fadeJob = serviceScope.launch {
            animatePlayerVolume(player.volume, 1f, 1000L)
        }
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
                fadeJob = serviceScope.launch {
                    animatePlayerVolume(player.volume, 0f, 500L)
                    player.pause()
                    player.seekTo(0)
                    isPausedByCall = true
                }
            }
        } else {
            if (isPausedByCall) {
                fadeJob?.cancel()
                fadeJob = serviceScope.launch {
                    player.volume = 0f
                    player.play()
                    isPausedByCall = false
                    animatePlayerVolume(0f, 1f, 1000L)
                }
            }
        }
    }

    private suspend fun animatePlayerVolume(from: Float, to: Float, durationMs: Long) {
        val steps = (durationMs / 20).toInt().coerceAtLeast(1)
        val stepSize = (to - from) / steps
        for (i in 1..steps) {
            player.volume = from + stepSize * i
            delay(20)
        }
        player.volume = to
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
