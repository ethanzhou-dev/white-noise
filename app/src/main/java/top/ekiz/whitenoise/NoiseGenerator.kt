package top.ekiz.whitenoise

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import java.util.Random

enum class NoiseType {
    WHITE, PINK, BROWN, BLUE, VIOLET
}

class NoiseGenerator {

    private var audioTrack: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val random = Random()

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @Volatile
    var volume: Float = 0.5f // 0.0 to 1.0

    @Volatile
    var noiseType: NoiseType = NoiseType.WHITE

    @Volatile
    var isPlaying = false
        private set

    fun start() {
        if (isPlaying) return
        isPlaying = true

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        job = scope.launch {
            val buffer = ShortArray(bufferSize)
            var lastBrownOut = 0.0
            var lastWhite = 0.0
            var b0 = 0.0
            var b1 = 0.0
            var b2 = 0.0
            var b3 = 0.0
            var b4 = 0.0
            var b5 = 0.0
            var b6 = 0.0

            while (isActive && isPlaying) {
                val currentVolume = volume
                val currentNoiseType = noiseType

                for (i in buffer.indices) {
                    val white = (random.nextDouble() * 2.0) - 1.0
                    var output = white

                    when (currentNoiseType) {
                        NoiseType.BROWN -> {
                            // Integrate white noise
                            val currentOut = (lastBrownOut + (0.02 * white)) / 1.02
                            lastBrownOut = currentOut
                            // Compensate volume drop
                            output = currentOut * 3.5
                        }
                        NoiseType.PINK -> {
                            b0 = 0.99886 * b0 + white * 0.0555179
                            b1 = 0.99332 * b1 + white * 0.0750759
                            b2 = 0.96900 * b2 + white * 0.1538520
                            b3 = 0.86650 * b3 + white * 0.3104856
                            b4 = 0.55000 * b4 + white * 0.5329522
                            b5 = -0.7616 * b5 - white * 0.0168980
                            val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                            b6 = white * 0.115926
                            output = pink * 0.11
                        }
                        NoiseType.BLUE -> {
                            output = (white - lastWhite) * 0.5
                        }
                        NoiseType.VIOLET -> {
                            output = (white - lastWhite)
                        }
                        else -> {
                            output = white
                        }
                    }
                    lastWhite = white

                    // Clip
                    if (output > 1.0) output = 1.0
                    if (output < -1.0) output = -1.0

                    buffer[i] = (output * Short.MAX_VALUE * currentVolume).toInt().toShort()
                }
                
                audioTrack?.write(buffer, 0, buffer.size)
            }
        }
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        job?.cancel()
        job = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
