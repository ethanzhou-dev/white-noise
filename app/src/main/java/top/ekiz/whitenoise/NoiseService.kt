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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.Player
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import kotlinx.coroutines.Job
import javax.inject.Inject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class NoiseService : MediaSessionService() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var timerManager: TimerManager

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
    val noiseProcessor = NoiseAudioProcessor()
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var audioManager: AudioManager
    private var modeListener: Any? = null
    private var isPausedByCall = false
    private var isPausedByDeviceDisconnect = false
    private var fadeJob: Job? = null
    
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val hasExternalDevice = addedDevices?.any { it.isExternalDevice() } == true
            if (hasExternalDevice && isPausedByDeviceDisconnect) {
                isPausedByDeviceDisconnect = false
                // Play with fade in
                fadeJob?.cancel()
                fadeJob = serviceScope.launch {
                    noiseProcessor.startFadeIn(1000L)
                    flushPlayerBuffer()
                    player.play()
                    isPausedByCall = false
                    // Quick hardware volume ramp (200ms) to suppress any HAL DMA buffer leaks,
                    // while PCM fade-in (1000ms) provides the artistic smooth start.
                    animatePlayerVolume(0f, 1f, 200L)
                }
            }
        }
    }

    private fun AudioDeviceInfo.isExternalDevice(): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
               type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
               type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
               type == AudioDeviceInfo.TYPE_USB_HEADSET ||
               type == AudioDeviceInfo.TYPE_USB_DEVICE ||
               type == AudioDeviceInfo.TYPE_HEARING_AID ||
               type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
               type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
               type == AudioDeviceInfo.TYPE_BLE_BROADCAST
    }

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
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                    isPausedByDeviceDisconnect = true
                } else if (playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    isPausedByDeviceDisconnect = false
                }
            }
        })

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
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
                    "PLAY_WITH_FADE" -> {
                        fadeJob?.cancel()
                        fadeJob = serviceScope.launch {
                            noiseProcessor.startFadeIn(1000L)
                            flushPlayerBuffer()
                            player.play()
                            isPausedByCall = false
                            // Quick hardware volume ramp (200ms) to suppress any HAL DMA buffer leaks
                            animatePlayerVolume(0f, 1f, 200L)
                        }
                    }
                    "PAUSE_WITH_FADE" -> {
                        fadeJob?.cancel()
                        fadeJob = serviceScope.launch {
                            animatePlayerVolume(player.volume, 0f, 500L)
                            player.pause()
                            player.seekTo(0)
                            isPausedByCall = false
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

            launch { 
                settingsDataStore.volumeFlow.collect { 
                    noiseProcessor.volume = it 
                    flushPlayerBufferIfNotPlaying()
                } 
            }
            launch { 
                settingsDataStore.noiseTypeFlow.collect { 
                    noiseProcessor.forceImmediateSwitch = !player.isPlaying
                    noiseProcessor.noiseType = it 
                    flushPlayerBufferIfNotPlaying()
                } 
            }
            launch { 
                settingsDataStore.balanceFlow.collect { 
                    noiseProcessor.balance = it 
                    flushPlayerBufferIfNotPlaying()
                } 
            }
            launch { 
                settingsDataStore.spatialAudioFlow.collect { 
                    noiseProcessor.isSpatialAudioEnabled = it 
                    flushPlayerBufferIfNotPlaying()
                } 
            }
            
            // Listen to Timer events from TimerManager (Single Source of Truth)
            launch {
                timerManager.timerEvents.collect { event ->
                    when (event) {
                        TimerEvent.START_FADE_OUT -> {
                            val durationMs = timerManager.activeRemainingMillis.value
                            if (durationMs > 0 && player.isPlaying) {
                                fadeJob?.cancel()
                                fadeJob = serviceScope.launch {
                                    animatePlayerVolume(player.volume, 0f, durationMs)
                                }
                            }
                        }
                        TimerEvent.TIMER_FINISHED -> {
                            fadeJob?.cancel()
                            player.pause()
                            player.seekTo(0)
                        }
                        TimerEvent.TIMER_CANCELLED -> {
                            if (player.isPlaying && !isPausedByCall) {
                                fadeJob?.cancel()
                                fadeJob = serviceScope.launch {
                                    animatePlayerVolume(player.volume, 1f, 1000L)
                                }
                            }
                        }
                    }
                }
            }
        }

        startCallMonitor()
    }

    private fun flushPlayerBuffer() {
        // ExoPlayer ignores seekTo if the target position equals the current position.
        // We toggle between 1ms and 0ms to guarantee a pipeline flush for audio settings / fade.
        val targetPos = if (player.currentPosition == 0L) 1L else 0L
        player.seekTo(targetPos)
    }

    private fun flushPlayerBufferIfNotPlaying() {
        if (!player.isPlaying) {
            flushPlayerBuffer()
        }
    }

    private fun startCallMonitor() {
        audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
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
                    noiseProcessor.startFadeIn(1000L)
                    flushPlayerBuffer()
                    player.play()
                    isPausedByCall = false
                    animatePlayerVolume(0f, 1f, 200L)
                }
            }
        }
    }

    private suspend fun animatePlayerVolume(from: Float, to: Float, durationMs: Long) {
        if (durationMs <= 0L) {
            player.volume = to
            return
        }
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
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
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
