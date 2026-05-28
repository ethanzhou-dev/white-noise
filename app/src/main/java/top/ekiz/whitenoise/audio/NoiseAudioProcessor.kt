package top.ekiz.whitenoise.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import top.ekiz.whitenoise.NoiseType
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class NoiseAudioProcessor : BaseAudioProcessor() {

    @Volatile var volume: Float = 0.5f // 0.0 to 1.0
    @Volatile var balance: Float = 0f // -1.0 to 1.0
    @Volatile var noiseType: NoiseType = NoiseType.WHITE
    @Volatile var isSpatialAudioEnabled: Boolean = true

    
    @Volatile private var currentFade = 0f
    @Volatile private var targetFade = 1f
    @Volatile private var fadeStep = 1f / 44100f

    fun fadeIn(durationMs: Long = 1000) {
        targetFade = 1f
        fadeStep = 1f / (44100f * (durationMs / 1000f).coerceAtLeast(0.1f))
    }
    
    fun fadeOut(durationMs: Long = 1000) {
        targetFade = 0f
        fadeStep = 1f / (44100f * (durationMs / 1000f).coerceAtLeast(0.1f))
    }

    private var currentNoiseType = noiseType
    private var fadingNoiseType: NoiseType? = null
    
    private var stateCurrent = NoiseGeneratorState()
    private var stateFadeOut = NoiseGeneratorState()
    
    private var crossfadeProgress = 1f
    private val crossfadeStep = 1f / (44100f * 1.5f) // 1.5s crossfade

    // Spatial Audio (Binaural) state
    private var lfoPhase: Double = 0.0
    private val lfoStep: Double = 2.0 * Math.PI * 0.05 / 44100.0 // 0.05 Hz
    private var headShadowL = 0f
    private var headShadowR = 0f

    private var rngState = System.nanoTime().toInt().let { if (it == 0) 1 else it }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // We output exactly what is requested (SilenceMediaSource will provide 16-bit PCM, usually stereo 44.1kHz or 48kHz)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Allocate or reuse output buffer
        val buffer = replaceOutputBuffer(remaining)
        
        val currentVolume = volume
        val currentBalance = balance
        val leftVol = 1f - max(0f, currentBalance)
        val rightVol = 1f + min(0f, currentBalance)
        
        val isStereo = inputAudioFormat.channelCount == 2
        val numShorts = remaining / 2 // 16-bit PCM
        
        // Advance input buffer position as we are completely replacing it
        inputBuffer.position(inputBuffer.position() + remaining)
        
        if (currentNoiseType != noiseType) {
            fadingNoiseType = currentNoiseType
            
            val temp = stateFadeOut
            stateFadeOut = stateCurrent
            stateCurrent = temp
            stateCurrent.reset()
            
            currentNoiseType = noiseType
            crossfadeProgress = 0f
        }

        var i = 0
        while (i < numShorts) {
            if (currentFade != targetFade) {
                if (currentFade < targetFade) {
                    currentFade = min(currentFade + fadeStep, targetFade)
                } else {
                    currentFade = max(currentFade - fadeStep, targetFade)
                }
            }
            val smoothFade = currentFade * currentFade * (3 - 2 * currentFade)
            val effectiveVolume = currentVolume * smoothFade

            // XorShift32 algorithm for ultra-fast pseudo-random noise
            rngState = rngState xor (rngState shl 13)
            rngState = rngState xor (rngState ushr 17)
            rngState = rngState xor (rngState shl 5)
            val centerWhite = (rngState ushr 8).toFloat() * 1.1920929E-7f - 1f

            rngState = rngState xor (rngState shl 13)
            rngState = rngState xor (rngState ushr 17)
            rngState = rngState xor (rngState shl 5)
            val sideWhiteL = (rngState ushr 8).toFloat() * 1.1920929E-7f - 1f

            val sideWhiteR = if (isStereo) {
                rngState = rngState xor (rngState shl 13)
                rngState = rngState xor (rngState ushr 17)
                rngState = rngState xor (rngState shl 5)
                (rngState ushr 8).toFloat() * 1.1920929E-7f - 1f
            } else {
                sideWhiteL
            }

            val whiteL = centerWhite * 0.8f + sideWhiteL * 0.6f
            val whiteR = centerWhite * 0.8f + sideWhiteR * 0.6f
            
            stateCurrent.process(whiteL, whiteR, currentNoiseType)
            var outputL = stateCurrent.outputL
            var outputR = stateCurrent.outputR

            if (crossfadeProgress < 1f) {
                crossfadeProgress += crossfadeStep
                if (crossfadeProgress >= 1f) {
                    crossfadeProgress = 1f
                    fadingNoiseType = null
                } else {
                    val fadeType = fadingNoiseType
                    if (fadeType != null) {
                        stateFadeOut.process(whiteL, whiteR, fadeType)
                        // Linear crossfade since both states share the same white noise source (highly correlated)
                        val fadeInGain = crossfadeProgress
                        val fadeOutGain = 1f - crossfadeProgress
                        
                        outputL = outputL * fadeInGain + stateFadeOut.outputL * fadeOutGain
                        outputR = outputR * fadeInGain + stateFadeOut.outputR * fadeOutGain
                    }
                }
            }

            // Binaural Spatializer (Optimal Solution without delay interpolation artifacts)
            if (isSpatialAudioEnabled && isStereo) {
                // 1. LFO Auto-Pan (0.05 Hz) - Double precision to prevent stuttering/static jumps
                lfoPhase += lfoStep
                if (lfoPhase > Math.PI * 2.0) {
                    lfoPhase -= Math.PI * 2.0
                }
                
                // Calculate LFO value (-1 to 1)
                val lfoVal = kotlin.math.sin(lfoPhase).toFloat()
                
                // 2. ILD (Interaural Level Difference)
                // When sound is on the right (lfoVal > 0), left volume decreases
                val levelL = 1.0f - max(0f, lfoVal * 0.25f) // Reduced depth (was 0.6)
                val levelR = 1.0f - max(0f, -lfoVal * 0.25f)
                
                // 3. HRTF-Lite (Head Shadow Low-Pass Filter)
                // When sound is on the opposite side, apply low pass filter.
                val lpfCoeffL = 1.0f - max(0f, lfoVal * 0.4f) // Reduced depth (was 0.85)
                headShadowL = headShadowL + lpfCoeffL * (outputL - headShadowL)
                
                val lpfCoeffR = 1.0f - max(0f, -lfoVal * 0.4f)
                headShadowR = headShadowR + lpfCoeffR * (outputR - headShadowR)
                
                outputL = headShadowL * levelL
                outputR = headShadowR * levelR
            }

            if (outputL > 1f) outputL = 1f
            if (outputL < -1f) outputL = -1f
            if (outputR > 1f) outputR = 1f
            if (outputR < -1f) outputR = -1f

            val sampleL = (outputL * leftVol * Short.MAX_VALUE * effectiveVolume).toInt().toShort()
            buffer.putShort(sampleL)
            i++

            if (isStereo) {
                val sampleR = (outputR * rightVol * Short.MAX_VALUE * effectiveVolume).toInt().toShort()
                buffer.putShort(sampleR)
                i++
            }
        }
        
        buffer.flip()
    }

    private class NoiseGeneratorState {
        var lastBrownOutL = 0f
        var lastWhiteL = 0f
        var b0L = 0f; var b1L = 0f; var b2L = 0f; var b3L = 0f; var b4L = 0f; var b5L = 0f; var b6L = 0f
        var lastBlackOutL = 0f
        var lastGreenL = 0f
        var lastWhiteLGreen = 0f
        
        var lastBrownOutR = 0f
        var lastWhiteR = 0f
        var b0R = 0f; var b1R = 0f; var b2R = 0f; var b3R = 0f; var b4R = 0f; var b5R = 0f; var b6R = 0f
        var lastBlackOutR = 0f
        var lastGreenR = 0f
        var lastWhiteRGreen = 0f

        var outputL = 0f
        var outputR = 0f

        fun reset() {
            lastBrownOutL = 0f; lastWhiteL = 0f; b0L = 0f; b1L = 0f; b2L = 0f; b3L = 0f; b4L = 0f; b5L = 0f; b6L = 0f; lastBlackOutL = 0f; lastGreenL = 0f; lastWhiteLGreen = 0f
            lastBrownOutR = 0f; lastWhiteR = 0f; b0R = 0f; b1R = 0f; b2R = 0f; b3R = 0f; b4R = 0f; b5R = 0f; b6R = 0f; lastBlackOutR = 0f; lastGreenR = 0f; lastWhiteRGreen = 0f
        }

        fun process(whiteL: Float, whiteR: Float, type: NoiseType) {
            var outL = whiteL
            var outR = whiteR

            when (type) {
                NoiseType.BROWN -> {
                    lastBrownOutL = (lastBrownOutL + (0.02f * whiteL)) / 1.02f
                    outL = lastBrownOutL * 3.5f
                    
                    lastBrownOutR = (lastBrownOutR + (0.02f * whiteR)) / 1.02f
                    outR = lastBrownOutR * 3.5f
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
                    outL = pinkL * 0.11f
                    
                    b0R = 0.99886f * b0R + whiteR * 0.0555179f
                    b1R = 0.99332f * b1R + whiteR * 0.0750759f
                    b2R = 0.96900f * b2R + whiteR * 0.1538520f
                    b3R = 0.86650f * b3R + whiteR * 0.3104856f
                    b4R = 0.55000f * b4R + whiteR * 0.5329522f
                    b5R = -0.7616f * b5R - whiteR * 0.0168980f
                    val pinkR = b0R + b1R + b2R + b3R + b4R + b5R + b6R + whiteR * 0.5362f
                    b6R = whiteR * 0.115926f
                    outR = pinkR * 0.11f
                }
                NoiseType.BLUE -> {
                    outL = (whiteL - lastWhiteL) * 0.5f
                    outR = (whiteR - lastWhiteR) * 0.5f
                }
                NoiseType.VIOLET -> {
                    outL = (whiteL - lastWhiteL)
                    outR = (whiteR - lastWhiteR)
                }
                NoiseType.GREY -> {
                    lastBrownOutL = (lastBrownOutL + (0.02f * whiteL)) / 1.02f
                    outL = (lastBrownOutL * 2.0f) + (whiteL - lastWhiteL) * 0.15f
                    
                    lastBrownOutR = (lastBrownOutR + (0.02f * whiteR)) / 1.02f
                    outR = (lastBrownOutR * 2.0f) + (whiteR - lastWhiteR) * 0.15f
                }
                NoiseType.GREEN -> {
                    val hpL = whiteL - lastWhiteLGreen
                    lastWhiteLGreen = whiteL
                    lastGreenL = (lastGreenL * 0.95f) + hpL * 0.05f
                    outL = lastGreenL * 15f
                    
                    val hpR = whiteR - lastWhiteRGreen
                    lastWhiteRGreen = whiteR
                    lastGreenR = (lastGreenR * 0.95f) + hpR * 0.05f
                    outR = lastGreenR * 15f
                }
                NoiseType.BLACK -> {
                    lastBrownOutL = (lastBrownOutL + (0.02f * whiteL)) / 1.02f
                    lastBlackOutL = (lastBlackOutL + (0.01f * lastBrownOutL)) / 1.01f
                    outL = lastBlackOutL * 10f
                    
                    lastBrownOutR = (lastBrownOutR + (0.02f * whiteR)) / 1.02f
                    lastBlackOutR = (lastBlackOutR + (0.01f * lastBrownOutR)) / 1.01f
                    outR = lastBlackOutR * 10f
                }
                NoiseType.WHITE -> {
                }
            }
            lastWhiteL = whiteL
            lastWhiteR = whiteR
            
            outputL = outL
            outputR = outR
        }
    }
}
