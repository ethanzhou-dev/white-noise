package top.ekiz.whitenoise

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlin.random.Random

enum class NoiseType {
    WHITE, PINK, BROWN, BLUE, VIOLET
}

class NoiseGenerator {

    private var audioTrack: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

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
            var lastBrownOut = 0f
            var lastWhite = 0f
            var b0 = 0f
            var b1 = 0f
            var b2 = 0f
            var b3 = 0f
            var b4 = 0f
            var b5 = 0f
            var b6 = 0f

            while (isActive && isPlaying) {
                val currentVolume = volume
                val currentNoiseType = noiseType

                if (currentNoiseType == NoiseType.WHITE) {
                    val maxAmplitude = (Short.MAX_VALUE * currentVolume).toInt()
                    val until = maxAmplitude + 1
                    val from = -maxAmplitude
                    for (i in buffer.indices) {
                        buffer[i] = Random.nextInt(from, until).toShort()
                    }
                } else {
                    for (i in buffer.indices) {
                        val white = (Random.nextFloat() * 2f) - 1f
                        var output = white

                        when (currentNoiseType) {
                            NoiseType.BROWN -> {
                                // Integrate white noise
                                val currentOut = (lastBrownOut + (0.02f * white)) / 1.02f
                                lastBrownOut = currentOut
                                // Compensate volume drop
                                output = currentOut * 3.5f
                            }
                            NoiseType.PINK -> {
                                b0 = 0.99886f * b0 + white * 0.0555179f
                                b1 = 0.99332f * b1 + white * 0.0750759f
                                b2 = 0.96900f * b2 + white * 0.1538520f
                                b3 = 0.86650f * b3 + white * 0.3104856f
                                b4 = 0.55000f * b4 + white * 0.5329522f
                                b5 = -0.7616f * b5 - white * 0.0168980f
                                val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
                                b6 = white * 0.115926f
                                output = pink * 0.11f
                            }
                            NoiseType.BLUE -> {
                                output = (white - lastWhite) * 0.5f
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
                        if (output > 1f) output = 1f
                        if (output < -1f) output = -1f

                        buffer[i] = (output * Short.MAX_VALUE * currentVolume).toInt().toShort()
                    }
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
