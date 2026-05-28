package top.ekiz.whitenoise

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

enum class NoiseType {
    WHITE, PINK, BROWN, BLUE, VIOLET, GREY, GREEN, BLACK
}

class NoiseGenerator {

    private var audioTrack: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val sampleRate = 44100
    private val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val trackBufferSize = minBufferSize * 4

    @Volatile
    var volume: Float = 0.5f // 0.0 to 1.0

    @Volatile
    var balance: Float = 0f // -1.0 to 1.0

    @Volatile
    var noiseType: NoiseType = NoiseType.WHITE

    @Volatile
    var isPlaying = false
        private set

    @Volatile
    private var isStopping = false

    @Volatile
    var isDucked = false

    @Volatile
    private var sleepFadeDurationFrames: Long = 0

    @Volatile
    private var sleepFadeRemainingFrames: Long = 0

    fun startSleepFadeOut(durationMillis: Long) {
        val frames = (durationMillis * sampleRate) / 1000L
        sleepFadeDurationFrames = frames
        sleepFadeRemainingFrames = frames
    }

    fun cancelSleepFadeOut() {
        sleepFadeDurationFrames = 0
        sleepFadeRemainingFrames = 0
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true
        isStopping = false

        if (job?.isActive == true) return

        job = scope.launch {
            sleepFadeDurationFrames = 0
            sleepFadeRemainingFrames = 0
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            
            val audioTrack = AudioTrack.Builder()
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
                .setBufferSizeInBytes(trackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()

            val numShorts = minBufferSize and 1.inv() // ensure even size for stereo (L, R interleaved)
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
            var lastBlackOutL = 0f
            var lastGreenL = 0f
            var lastWhiteLGreen = 0f
            
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
            var lastBlackOutR = 0f
            var lastGreenR = 0f
            var lastWhiteRGreen = 0f

            var currentFade = 0f
            var targetFade = 1f
            val fadeStep = 1f / (sampleRate * 0.5f) // 0.5 seconds fade

            var currentTimerFade = 1f
            val timerFadeRestoreStep = 1f / (sampleRate * 2f) // 2 seconds restore fade

            var activeNoiseType = noiseType

            // XorShift32 state for fast PRNG
            var rngState = System.nanoTime().toInt()
            if (rngState == 0) rngState = 1

            while (isActive) {
                if (isStopping && currentFade <= 0f) {
                    break
                }

                if (!isStopping) {
                    val duckTarget = if (isDucked) 0.2f else 1f
                    if (activeNoiseType != noiseType) {
                        targetFade = 0f
                        if (currentFade <= 0f) {
                            activeNoiseType = noiseType
                            targetFade = duckTarget
                            // Reset states to avoid pops from accumulated values
                            lastBrownOutL = 0f; lastWhiteL = 0f; b0L = 0f; b1L = 0f; b2L = 0f; b3L = 0f; b4L = 0f; b5L = 0f; b6L = 0f; lastBlackOutL = 0f; lastGreenL = 0f; lastWhiteLGreen = 0f
                            lastBrownOutR = 0f; lastWhiteR = 0f; b0R = 0f; b1R = 0f; b2R = 0f; b3R = 0f; b4R = 0f; b5R = 0f; b6R = 0f; lastBlackOutR = 0f; lastGreenR = 0f; lastWhiteRGreen = 0f
                        }
                    } else {
                        targetFade = duckTarget
                    }
                } else {
                    targetFade = 0f
                }

                val currentVolume = volume
                val currentBalance = balance
                val leftVol = 1f - currentBalance.coerceAtLeast(0f)
                val rightVol = 1f + currentBalance.coerceAtMost(0f)

                for (i in 0 until numShorts step 2) {
                    // Update fade envelope per sample
                    if (currentFade < targetFade) {
                        currentFade = (currentFade + fadeStep).coerceAtMost(targetFade)
                    } else if (currentFade > targetFade) {
                        currentFade = (currentFade - fadeStep).coerceAtLeast(targetFade)
                    }

                    // Apply smooth S-curve to fade for more natural sound (optional, but sounds better)
                    val smoothFade = currentFade * currentFade * (3f - 2f * currentFade)

                    // Timer fade logic
                    var targetTimerFade = 1f
                    if (sleepFadeDurationFrames > 0) {
                        if (sleepFadeRemainingFrames > 0) {
                            targetTimerFade = sleepFadeRemainingFrames.toFloat() / sleepFadeDurationFrames.toFloat()
                            sleepFadeRemainingFrames--
                        } else {
                            targetTimerFade = 0f
                        }
                    }

                    if (currentTimerFade < targetTimerFade) {
                        currentTimerFade = (currentTimerFade + timerFadeRestoreStep).coerceAtMost(targetTimerFade)
                    } else {
                        currentTimerFade = targetTimerFade
                    }
                    val naturalTimerFade = currentTimerFade * currentTimerFade

                    // XorShift32 algorithm for ultra-fast pseudo-random noise
                    rngState = rngState xor (rngState shl 13)
                    rngState = rngState xor (rngState ushr 17)
                    rngState = rngState xor (rngState shl 5)
                    val centerWhite = (rngState ushr 8).toFloat() * 1.1920929E-7f - 1f

                    rngState = rngState xor (rngState shl 13)
                    rngState = rngState xor (rngState ushr 17)
                    rngState = rngState xor (rngState shl 5)
                    val sideWhiteL = (rngState ushr 8).toFloat() * 1.1920929E-7f - 1f

                    rngState = rngState xor (rngState shl 13)
                    rngState = rngState xor (rngState ushr 17)
                    rngState = rngState xor (rngState shl 5)
                    val sideWhiteR = (rngState ushr 8).toFloat() * 1.1920929E-7f - 1f

                    // Natural correlation blending: 80% center, 60% side
                    // 0.8^2 + 0.6^2 = 1.0 (preserves power to prevent clipping)
                    val whiteL = centerWhite * 0.8f + sideWhiteL * 0.6f
                    val whiteR = centerWhite * 0.8f + sideWhiteR * 0.6f
                    
                    var outputL = whiteL
                    var outputR = whiteR

                    when (activeNoiseType) {
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
                        NoiseType.GREY -> {
                            val currentOutL = (lastBrownOutL + (0.02f * whiteL)) / 1.02f
                            lastBrownOutL = currentOutL
                            outputL = (currentOutL * 2.0f) + (whiteL - lastWhiteL) * 0.15f
                            
                            val currentOutR = (lastBrownOutR + (0.02f * whiteR)) / 1.02f
                            lastBrownOutR = currentOutR
                            outputR = (currentOutR * 2.0f) + (whiteR - lastWhiteR) * 0.15f
                        }
                        NoiseType.GREEN -> {
                            val hpL = whiteL - lastWhiteLGreen
                            lastWhiteLGreen = whiteL
                            val lpL = (lastGreenL * 0.95f) + hpL * 0.05f
                            lastGreenL = lpL
                            outputL = lpL * 15f
                            
                            val hpR = whiteR - lastWhiteRGreen
                            lastWhiteRGreen = whiteR
                            val lpR = (lastGreenR * 0.95f) + hpR * 0.05f
                            lastGreenR = lpR
                            outputR = lpR * 15f
                        }
                        NoiseType.BLACK -> {
                            val brownL = (lastBrownOutL + (0.02f * whiteL)) / 1.02f
                            lastBrownOutL = brownL
                            val blackL = (lastBlackOutL + (0.01f * brownL)) / 1.01f
                            lastBlackOutL = blackL
                            outputL = blackL * 10f
                            
                            val brownR = (lastBrownOutR + (0.02f * whiteR)) / 1.02f
                            lastBrownOutR = brownR
                            val blackR = (lastBlackOutR + (0.01f * brownR)) / 1.01f
                            lastBlackOutR = blackR
                            outputR = blackR * 10f
                        }
                        NoiseType.WHITE -> {
                            // Already generated
                        }
                    }
                    lastWhiteL = whiteL
                    lastWhiteR = whiteR

                    if (outputL > 1f) outputL = 1f
                    if (outputL < -1f) outputL = -1f
                    if (outputR > 1f) outputR = 1f
                    if (outputR < -1f) outputR = -1f

                    buffer[i] = (outputL * leftVol * Short.MAX_VALUE * currentVolume * smoothFade * naturalTimerFade).toInt().toShort()
                    buffer[i + 1] = (outputR * rightVol * Short.MAX_VALUE * currentVolume * smoothFade * naturalTimerFade).toInt().toShort()
                }
                
                audioTrack.write(buffer, 0, buffer.size)
            }

            audioTrack.stop()
            audioTrack.release()
            isPlaying = false
        }
    }

    fun stop() {
        if (!isPlaying) return
        isStopping = true
    }
}
