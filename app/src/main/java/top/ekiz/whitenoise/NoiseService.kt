package top.ekiz.whitenoise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class NoiseService : Service() {

    private val binder = LocalBinder()
    private val generator = NoiseGenerator()

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
        generator.start()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun stopPlayback() {
        generator.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun setVolume(vol: Float) {
        generator.volume = vol
    }

    fun setNoiseType(type: NoiseType) {
        generator.noiseType = type
    }
    
    fun isPlaying(): Boolean {
        return generator.isPlaying
    }
    
    fun getVolume(): Float {
        return generator.volume
    }
    
    fun getNoiseType(): NoiseType {
        return generator.noiseType
    }

    override fun onDestroy() {
        generator.stop()
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
