package top.ekiz.whitenoise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NoiseService : Service() {

    private val binder = LocalBinder()
    private val generator = NoiseGenerator()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var sleepTimerJob: Job? = null
    
    private var sleepEndTimeMillis: Long = 0L
    private var sleepTotalTimeMillis: Long = 0L
    private var sleepRemainingPausedMillis: Long = 0L
    private var isSleepTimerPaused: Boolean = false
    
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPausedByFocusLoss = false

    companion object {
        const val CHANNEL_ID = "WhiteNoiseChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "top.ekiz.whitenoise.ACTION_STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): NoiseService = this@NoiseService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun startPlayback() {
        if (requestAudioFocus()) {
            isPausedByFocusLoss = false
            generator.isDucked = false
            generator.start()
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    fun stopPlayback() {
        generator.cancelSleepFadeOut()
        generator.stop()
        abandonAudioFocus()
        sleepTimerJob?.cancel()
        sleepEndTimeMillis = 0L
        sleepTotalTimeMillis = 0L
        sleepRemainingPausedMillis = 0L
        isSleepTimerPaused = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun setVolume(vol: Float) {
        generator.volume = vol
    }

    fun setBalance(balance: Float) {
        generator.balance = balance
    }

    fun setNoiseType(type: NoiseType) {
        generator.noiseType = type
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        generator.cancelSleepFadeOut()
        if (minutes > 0) {
            sleepTotalTimeMillis = minutes * 60 * 1000L
            sleepRemainingPausedMillis = sleepTotalTimeMillis
            isSleepTimerPaused = true
            sleepEndTimeMillis = 0L
        } else {
            sleepEndTimeMillis = 0L
            sleepTotalTimeMillis = 0L
            sleepRemainingPausedMillis = 0L
            isSleepTimerPaused = false
        }
    }
    
    fun startSleepTimer() {
        if (isSleepTimerPaused && sleepRemainingPausedMillis > 0) {
            sleepEndTimeMillis = System.currentTimeMillis() + sleepRemainingPausedMillis
            isSleepTimerPaused = false
            sleepTimerJob?.cancel()
            sleepTimerJob = serviceScope.launch {
                val fadeDuration = 60_000L
                if (sleepRemainingPausedMillis > fadeDuration) {
                    delay(sleepRemainingPausedMillis - fadeDuration)
                    generator.startSleepFadeOut(fadeDuration)
                    delay(fadeDuration)
                } else {
                    generator.startSleepFadeOut(sleepRemainingPausedMillis)
                    delay(sleepRemainingPausedMillis)
                }
                if (isPlaying()) {
                    stopPlayback()
                }
                sleepEndTimeMillis = 0L
                sleepTotalTimeMillis = 0L
                sleepRemainingPausedMillis = 0L
                isSleepTimerPaused = false
            }
        }
    }
    
    fun pauseSleepTimer() {
        if (sleepEndTimeMillis > 0L && !isSleepTimerPaused) {
            sleepTimerJob?.cancel()
            generator.cancelSleepFadeOut()
            sleepRemainingPausedMillis = maxOf(0L, sleepEndTimeMillis - System.currentTimeMillis())
            isSleepTimerPaused = true
            sleepEndTimeMillis = 0L
        }
    }
    
    fun isSleepTimerRunning(): Boolean {
        return sleepEndTimeMillis > 0L && !isSleepTimerPaused
    }
    
    fun getSleepRemainingMillis(): Long {
        return if (isSleepTimerPaused) {
            sleepRemainingPausedMillis
        } else if (sleepEndTimeMillis > 0L) {
            maxOf(0L, sleepEndTimeMillis - System.currentTimeMillis())
        } else {
            0L
        }
    }
    
    fun getSleepTotalMillis(): Long {
        return sleepTotalTimeMillis
    }
    
    fun isPlaying(): Boolean {
        return generator.isPlaying
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isPausedByFocusLoss) {
                    isPausedByFocusLoss = false
                    startPlayback()
                } else if (generator.isDucked) {
                    generator.isDucked = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying()) {
                    generator.isDucked = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying()) {
                    isPausedByFocusLoss = true
                    generator.stop()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 用户希望与其他媒体（音乐/视频）混音，所以这里我们忽略永久焦点丢失，不停止播放
                // 注意：这会导致系统焦点转移给音乐/视频 App。当其他 App 结束后，如果来通知，我们将无法触发 Ducking，直到用户重新播放。
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            val result = audioManager.requestAudioFocus(request)
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    override fun onDestroy() {
        generator.stop()
        sleepTimerJob?.cancel()
        abandonAudioFocus()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows playback controls for White Noise"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, NoiseService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("白噪音")
            .setContentText("正在播放...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
