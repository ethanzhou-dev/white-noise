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
    @Volatile var isSpatialAudioEnabled: Boolean = false
    @Volatile var forceImmediateSwitch: Boolean = false


    private var currentNoiseType = noiseType
    private var fadingNoiseType: NoiseType? = null

    fun initialize(type: NoiseType) {
        noiseType = type
        currentNoiseType = type
        fadingNoiseType = null
        crossfadeProgress = 1f
        forceImmediateSwitch = false
        stateCurrent.reset()
        stateFadeOut.reset()
    }
    
    private var stateCurrent = NoiseGeneratorState()
    private var stateFadeOut = NoiseGeneratorState()
    
    private var crossfadeProgress = 1f
    private val crossfadeStep = 1f / (44100f * 1.5f) // 1.5s crossfade

    // Spatial Audio (Binaural) state
    private var lfoPhase: Double = 0.0
    private val lfoStep: Double = 2.0 * Math.PI * 0.05 / 44100.0 // 0.05 Hz
    private var headShadowL = 0f
    private var headShadowR = 0f

    private var rngState: Long = System.nanoTime().let { if (it == 0L) 1L else it }

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
            if (forceImmediateSwitch) {
                currentNoiseType = noiseType
                fadingNoiseType = null
                crossfadeProgress = 1f
                stateCurrent.reset()
                stateFadeOut.reset()
                forceImmediateSwitch = false
            } else {
                fadingNoiseType = currentNoiseType
                
                val temp = stateFadeOut
                stateFadeOut = stateCurrent
                stateCurrent = temp
                stateCurrent.reset()
                
                currentNoiseType = noiseType
                crossfadeProgress = 0f
            }
        } else {
            forceImmediateSwitch = false
        }

        var i = 0
        while (i < numShorts) {
            val effectiveVolume = currentVolume

            // XorShift64 algorithm for ultra-fast pseudo-random noise (period of ~4.4 million years)
            rngState = rngState xor (rngState shl 13)
            rngState = rngState xor (rngState ushr 7)
            rngState = rngState xor (rngState shl 17)
            val centerWhite = (rngState ushr 40).toFloat() * 1.1920929E-7f - 1f

            rngState = rngState xor (rngState shl 13)
            rngState = rngState xor (rngState ushr 7)
            rngState = rngState xor (rngState shl 17)
            val sideWhiteL = (rngState ushr 40).toFloat() * 1.1920929E-7f - 1f

            val sideWhiteR = if (isStereo) {
                rngState = rngState xor (rngState shl 13)
                rngState = rngState xor (rngState ushr 7)
                rngState = rngState xor (rngState shl 17)
                (rngState ushr 40).toFloat() * 1.1920929E-7f - 1f
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
        var lastBrownOutL = 0.0
        var lastWhiteL = 0.0
        var b0L = 0.0; var b1L = 0.0; var b2L = 0.0; var b3L = 0.0; var b4L = 0.0; var b5L = 0.0; var b6L = 0.0
        var lastBlackOutL = 0.0
        var lastGreenL = 0.0
        var lastWhiteLGreen = 0.0
        
        var lastBrownOutR = 0.0
        var lastWhiteR = 0.0
        var b0R = 0.0; var b1R = 0.0; var b2R = 0.0; var b3R = 0.0; var b4R = 0.0; var b5R = 0.0; var b6R = 0.0
        var lastBlackOutR = 0.0
        var lastGreenR = 0.0
        var lastWhiteRGreen = 0.0

        var outputL = 0f
        var outputR = 0f

        fun reset() {
            lastBrownOutL = 0.0; lastWhiteL = 0.0; b0L = 0.0; b1L = 0.0; b2L = 0.0; b3L = 0.0; b4L = 0.0; b5L = 0.0; b6L = 0.0; lastBlackOutL = 0.0; lastGreenL = 0.0; lastWhiteLGreen = 0.0
            lastBrownOutR = 0.0; lastWhiteR = 0.0; b0R = 0.0; b1R = 0.0; b2R = 0.0; b3R = 0.0; b4R = 0.0; b5R = 0.0; b6R = 0.0; lastBlackOutR = 0.0; lastGreenR = 0.0; lastWhiteRGreen = 0.0
        }

        fun process(whiteL: Float, whiteR: Float, type: NoiseType) {
            val wL = whiteL.toDouble()
            val wR = whiteR.toDouble()
            var outL = wL
            var outR = wR

            when (type) {
                NoiseType.BROWN -> {
                    lastBrownOutL = (lastBrownOutL + (0.02 * wL)) / 1.02
                    outL = lastBrownOutL * 3.5
                    
                    lastBrownOutR = (lastBrownOutR + (0.02 * wR)) / 1.02
                    outR = lastBrownOutR * 3.5
                }
                NoiseType.PINK -> {
                    b0L = 0.99886 * b0L + wL * 0.0555179
                    b1L = 0.99332 * b1L + wL * 0.0750759
                    b2L = 0.96900 * b2L + wL * 0.1538520
                    b3L = 0.86650 * b3L + wL * 0.3104856
                    b4L = 0.55000 * b4L + wL * 0.5329522
                    b5L = -0.7616 * b5L - wL * 0.0168980
                    val pinkL = b0L + b1L + b2L + b3L + b4L + b5L + b6L + wL * 0.5362
                    b6L = wL * 0.115926
                    outL = pinkL * 0.11
                    
                    b0R = 0.99886 * b0R + wR * 0.0555179
                    b1R = 0.99332 * b1R + wR * 0.0750759
                    b2R = 0.96900 * b2R + wR * 0.1538520
                    b3R = 0.86650 * b3R + wR * 0.3104856
                    b4R = 0.55000 * b4R + wR * 0.5329522
                    b5R = -0.7616 * b5R - wR * 0.0168980
                    val pinkR = b0R + b1R + b2R + b3R + b4R + b5R + b6R + wR * 0.5362
                    b6R = wR * 0.115926
                    outR = pinkR * 0.11
                }
                NoiseType.BLUE -> {
                    outL = (wL - lastWhiteL) * 0.5
                    outR = (wR - lastWhiteR) * 0.5
                }
                NoiseType.VIOLET -> {
                    outL = (wL - lastWhiteL)
                    outR = (wR - lastWhiteR)
                }
                NoiseType.GREY -> {
                    lastBrownOutL = (lastBrownOutL + (0.02 * wL)) / 1.02
                    outL = (lastBrownOutL * 2.0) + (wL - lastWhiteL) * 0.15
                    
                    lastBrownOutR = (lastBrownOutR + (0.02 * wR)) / 1.02
                    outR = (lastBrownOutR * 2.0) + (wR - lastWhiteR) * 0.15
                }
                NoiseType.GREEN -> {
                    val hpL = wL - lastWhiteLGreen
                    lastWhiteLGreen = wL
                    lastGreenL = (lastGreenL * 0.95) + hpL * 0.05
                    outL = lastGreenL * 15.0
                    
                    val hpR = wR - lastWhiteRGreen
                    lastWhiteRGreen = wR
                    lastGreenR = (lastGreenR * 0.95) + hpR * 0.05
                    outR = lastGreenR * 15.0
                }
                NoiseType.BLACK -> {
                    lastBrownOutL = (lastBrownOutL + (0.02 * wL)) / 1.02
                    lastBlackOutL = (lastBlackOutL + (0.01 * lastBrownOutL)) / 1.01
                    outL = lastBlackOutL * 10.0
                    
                    lastBrownOutR = (lastBrownOutR + (0.02 * wR)) / 1.02
                    lastBlackOutR = (lastBlackOutR + (0.01 * lastBrownOutR)) / 1.01
                    outR = lastBlackOutR * 10.0
                }
                NoiseType.WHITE -> {
                }
            }
            lastWhiteL = wL
            lastWhiteR = wR
            
            outputL = outL.toFloat()
            outputR = outR.toFloat()
        }
    }
}
