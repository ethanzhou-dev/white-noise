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
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @Volatile
    var volume: Float = 0.5f // 0.0 to 1.0

    @Volatile
    var balance: Float = 0f // -1.0 to 1.0

    @Volatile
    var stereoWidth: Float = 1f // 0.0 to 1.0

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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        job = scope.launch {
            val numShorts = bufferSize and 1.inv() // ensure even size for stereo (L, R interleaved)
            val buffer = ShortArray(numShorts)
            
            // Left state
            var lastBrownOutL = 0f
            var lastWhiteL = 0f
            var b0L = 0f
            var b1L = 0f
            var b2L = 0f
            var b3L = 0f
            var b4L = 0f
            var b5L = 0f
            var b6L = 0f
            
            // Right state
            var lastBrownOutR = 0f
            var lastWhiteR = 0f
            var b0R = 0f
            var b1R = 0f
            var b2R = 0f
            var b3R = 0f
            var b4R = 0f
            var b5R = 0f
            var b6R = 0f

            while (isActive && isPlaying) {
                val currentVolume = volume
                val currentNoiseType = noiseType
                val currentBalance = balance
                val currentStereoWidth = stereoWidth

                val isDefaultPan = currentBalance == 0f && currentStereoWidth == 1f

                if (currentNoiseType == NoiseType.WHITE && isDefaultPan) {
                    val maxAmplitude = (Short.MAX_VALUE * currentVolume).toInt()
                    val until = maxAmplitude + 1
                    val from = -maxAmplitude
                    for (i in 0 until numShorts step 2) {
                        buffer[i] = Random.nextInt(from, until).toShort()
                        buffer[i + 1] = Random.nextInt(from, until).toShort()
                    }
                } else {
                    val leftVol = 1f - currentBalance.coerceAtLeast(0f)
                    val rightVol = 1f + currentBalance.coerceAtMost(0f)

                    for (i in 0 until numShorts step 2) {
                        val whiteL = (Random.nextFloat() * 2f) - 1f
                        val whiteR = (Random.nextFloat() * 2f) - 1f
                        
                        var outputL = whiteL
                        var outputR = whiteR

                        when (currentNoiseType) {
                            NoiseType.BROWN -> {
                                val currentOutL = (lastBrownOutL + (0.02f * whiteL)) / 1.02f
                                lastBrownOutL = currentOutL
                                outputL = currentOutL * 3.5f
                                
                                val currentOutR = (lastBrownOutR + (0.02f * whiteR)) / 1.02f
                                lastBrownOutR = currentOutR
                                outputR = currentOutR * 3.5f
                            }
                            NoiseType.PINK -> {
                                b0L = 0.99886f * b0L + whiteL * 0.0555179f
                                b1L = 0.99332f * b1L + whiteL * 0.0750759f
                                b2L = 0.96900f * b2L + whiteL * 0.1538520f
                                b3L = 0.86650f * b3L + whiteL * 0.3104856f
                                b4L = 0.55000f * b4L + whiteL * 0.5329522f
                                b5L = -0.7616f * b5L - whiteL * 0.0168980f
                                val pinkL = b0L + b1L + b2L + b3L + b4L + b5L + b6L + whiteL * 0.5362f
                                b6L = whiteL * 0.115926f
                                outputL = pinkL * 0.11f
                                
                                b0R = 0.99886f * b0R + whiteR * 0.0555179f
                                b1R = 0.99332f * b1R + whiteR * 0.0750759f
                                b2R = 0.96900f * b2R + whiteR * 0.1538520f
                                b3R = 0.86650f * b3R + whiteR * 0.3104856f
                                b4R = 0.55000f * b4R + whiteR * 0.5329522f
                                b5R = -0.7616f * b5R - whiteR * 0.0168980f
                                val pinkR = b0R + b1R + b2R + b3R + b4R + b5R + b6R + whiteR * 0.5362f
                                b6R = whiteR * 0.115926f
                                outputR = pinkR * 0.11f
                            }
                            NoiseType.BLUE -> {
                                outputL = (whiteL - lastWhiteL) * 0.5f
                                outputR = (whiteR - lastWhiteR) * 0.5f
                            }
                            NoiseType.VIOLET -> {
                                outputL = (whiteL - lastWhiteL)
                                outputR = (whiteR - lastWhiteR)
                            }
                            else -> {}
                        }
                        lastWhiteL = whiteL
                        lastWhiteR = whiteR

                        if (currentStereoWidth != 1f) {
                            val mid = (outputL + outputR) * 0.5f
                            val side = (outputL - outputR) * 0.5f
                            outputL = mid + side * currentStereoWidth
                            outputR = mid - side * currentStereoWidth
                        }

                        if (outputL > 1f) outputL = 1f
                        if (outputL < -1f) outputL = -1f
                        if (outputR > 1f) outputR = 1f
                        if (outputR < -1f) outputR = -1f

                        buffer[i] = (outputL * leftVol * Short.MAX_VALUE * currentVolume).toInt().toShort()
                        buffer[i + 1] = (outputR * rightVol * Short.MAX_VALUE * currentVolume).toInt().toShort()
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
