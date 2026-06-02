package top.ekiz.whitenoise.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import top.ekiz.whitenoise.NoiseType
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NoiseAudioProcessor : BaseAudioProcessor() {

    @Volatile var volume: Float = 0.5f // 0.0 to 1.0
    @Volatile var balance: Float = 0f // -1.0 to 1.0
    @Volatile var noiseType: NoiseType = NoiseType.WHITE
    @Volatile var isSpatialAudioEnabled: Boolean = false
    @Volatile var forceImmediateSwitch: Boolean = false

    @Volatile var pcmFadeMultiplier: Float = 1f
    @Volatile private var pcmFadeStep: Float = 0f

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

    override fun onFlush() {
        super.onFlush()
        stateCurrent.reset()
        stateFadeOut.reset()
    }

    fun startFadeIn(durationMs: Long) {
        if (durationMs <= 0L) {
            pcmFadeMultiplier = 1f
            pcmFadeStep = 0f
            return
        }
        pcmFadeMultiplier = 0f
        val sr = if (inputAudioFormat.sampleRate != -1) {
            inputAudioFormat.sampleRate
        } else {
            44100
        }
        // One sample per channel, but we process per frame (1 loop iteration = 1 frame)
        // Wait, the while loop increments `i` by 1 for L and 1 for R. 
        // We will increment pcmFadeMultiplier once per frame (while loop iteration).
        val totalFrames = (durationMs / 1000f) * sr
        pcmFadeStep = 1f / totalFrames
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
        val fadeStep = pcmFadeStep
        var currentFadeMult = pcmFadeMultiplier

        var i = 0
        while (i < numShorts) {
            if (currentFadeMult < 1f && fadeStep > 0f) {
                currentFadeMult = java.lang.Math.min(1f, currentFadeMult + fadeStep)
            }
            val effectiveVolume = currentVolume * currentFadeMult

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
        
        pcmFadeMultiplier = currentFadeMult
        
        buffer.flip()
    }

    private class NoiseGeneratorState {
        var lastBrownOutL = 0.0
        var lastDeepBrownOutL = 0.0
        var lastWhiteL = 0.0
        var b0L = 0.0; var b1L = 0.0; var b2L = 0.0; var b3L = 0.0; var b4L = 0.0; var b5L = 0.0; var b6L = 0.0
        var b0BlueL = 0.0; var b1BlueL = 0.0; var b2BlueL = 0.0; var b3BlueL = 0.0; var b4BlueL = 0.0; var b5BlueL = 0.0; var b6BlueL = 0.0
        var lastBluePinkL = 0.0
        var lastBlackOutL = 0.0
        var greenX1L = 0.0; var greenX2L = 0.0; var greenY1L = 0.0; var greenY2L = 0.0
        var greyLx1L = 0.0; var greyLx2L = 0.0; var greyLy1L = 0.0; var greyLy2L = 0.0
        var greyHx1L = 0.0; var greyHx2L = 0.0; var greyHy1L = 0.0; var greyHy2L = 0.0
        
        var lastBrownOutR = 0.0
        var lastDeepBrownOutR = 0.0
        var lastWhiteR = 0.0
        var b0R = 0.0; var b1R = 0.0; var b2R = 0.0; var b3R = 0.0; var b4R = 0.0; var b5R = 0.0; var b6R = 0.0
        var b0BlueR = 0.0; var b1BlueR = 0.0; var b2BlueR = 0.0; var b3BlueR = 0.0; var b4BlueR = 0.0; var b5BlueR = 0.0; var b6BlueR = 0.0
        var lastBluePinkR = 0.0
        var lastBlackOutR = 0.0
        var greenX1R = 0.0; var greenX2R = 0.0; var greenY1R = 0.0; var greenY2R = 0.0
        var greyLx1R = 0.0; var greyLx2R = 0.0; var greyLy1R = 0.0; var greyLy2R = 0.0
        var greyHx1R = 0.0; var greyHx2R = 0.0; var greyHy1R = 0.0; var greyHy2R = 0.0

        // Velvet
        var velvetCounterL = 0
        var velvetGridL = 22
        var velvetPulseL = 0.0
        var velvetCounterR = 0
        var velvetGridR = 22
        var velvetPulseR = 0.0

        // Ocean Waves
        var oceanPhase = 0.0
        var oceanLpfL = 0.0
        var oceanLpfR = 0.0
        var lastOceanBrownL = 0.0
        var lastOceanBrownR = 0.0

        // Binaural Beats
        var binauralPhaseL = 0.0
        var binauralPhaseR = 0.0

        var outputL = 0f
        var outputR = 0f

        fun reset() {
            lastBrownOutL = 0.0; lastDeepBrownOutL = 0.0; lastWhiteL = 0.0; b0L = 0.0; b1L = 0.0; b2L = 0.0; b3L = 0.0; b4L = 0.0; b5L = 0.0; b6L = 0.0; lastBlackOutL = 0.0
            lastBrownOutR = 0.0; lastDeepBrownOutR = 0.0; lastWhiteR = 0.0; b0R = 0.0; b1R = 0.0; b2R = 0.0; b3R = 0.0; b4R = 0.0; b5R = 0.0; b6R = 0.0; lastBlackOutR = 0.0
            b0BlueL = 0.0; b1BlueL = 0.0; b2BlueL = 0.0; b3BlueL = 0.0; b4BlueL = 0.0; b5BlueL = 0.0; b6BlueL = 0.0; lastBluePinkL = 0.0
            b0BlueR = 0.0; b1BlueR = 0.0; b2BlueR = 0.0; b3BlueR = 0.0; b4BlueR = 0.0; b5BlueR = 0.0; b6BlueR = 0.0; lastBluePinkR = 0.0
            
            greenX1L = 0.0; greenX2L = 0.0; greenY1L = 0.0; greenY2L = 0.0
            greenX1R = 0.0; greenX2R = 0.0; greenY1R = 0.0; greenY2R = 0.0
            greyLx1L = 0.0; greyLx2L = 0.0; greyLy1L = 0.0; greyLy2L = 0.0
            greyLx1R = 0.0; greyLx2R = 0.0; greyLy1R = 0.0; greyLy2R = 0.0
            greyHx1L = 0.0; greyHx2L = 0.0; greyHy1L = 0.0; greyHy2L = 0.0
            greyHx1R = 0.0; greyHx2R = 0.0; greyHy1R = 0.0; greyHy2R = 0.0

            velvetCounterL = 0; velvetPulseL = 0.0
            velvetCounterR = 0; velvetPulseR = 0.0
            oceanPhase = 0.0; oceanLpfL = 0.0; oceanLpfR = 0.0; lastOceanBrownL = 0.0; lastOceanBrownR = 0.0
            binauralPhaseL = 0.0; binauralPhaseR = 0.0
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
                NoiseType.DEEP_BROWN -> {
                    lastDeepBrownOutL = (lastDeepBrownOutL + (0.01 * wL)) / 1.01
                    outL = lastDeepBrownOutL * 5.0
                    
                    lastDeepBrownOutR = (lastDeepBrownOutR + (0.01 * wR)) / 1.01
                    outR = lastDeepBrownOutR * 5.0
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
                    b0BlueL = 0.99886 * b0BlueL + wL * 0.0555179
                    b1BlueL = 0.99332 * b1BlueL + wL * 0.0750759
                    b2BlueL = 0.96900 * b2BlueL + wL * 0.1538520
                    b3BlueL = 0.86650 * b3BlueL + wL * 0.3104856
                    b4BlueL = 0.55000 * b4BlueL + wL * 0.5329522
                    b5BlueL = -0.7616 * b5BlueL - wL * 0.0168980
                    val pinkL = b0BlueL + b1BlueL + b2BlueL + b3BlueL + b4BlueL + b5BlueL + b6BlueL + wL * 0.5362
                    b6BlueL = wL * 0.115926
                    outL = (pinkL - lastBluePinkL) * 2.0
                    lastBluePinkL = pinkL
                    
                    b0BlueR = 0.99886 * b0BlueR + wR * 0.0555179
                    b1BlueR = 0.99332 * b1BlueR + wR * 0.0750759
                    b2BlueR = 0.96900 * b2BlueR + wR * 0.1538520
                    b3BlueR = 0.86650 * b3BlueR + wR * 0.3104856
                    b4BlueR = 0.55000 * b4BlueR + wR * 0.5329522
                    b5BlueR = -0.7616 * b5BlueR - wR * 0.0168980
                    val pinkR = b0BlueR + b1BlueR + b2BlueR + b3BlueR + b4BlueR + b5BlueR + b6BlueR + wR * 0.5362
                    b6BlueR = wR * 0.115926
                    outR = (pinkR - lastBluePinkR) * 2.0
                    lastBluePinkR = pinkR
                }
                NoiseType.VIOLET -> {
                    outL = (wL - lastWhiteL)
                    outR = (wR - lastWhiteR)
                }
                NoiseType.GREY -> {
                    // Inverse A-Weighting Approximation via Biquad Shelving Filters
                    // 1. Low Shelf (Fc=250Hz, Gain=+15dB)
                    val midL = 1.02268 * wL - 1.96607 * greyLx1L + 0.94636 * greyLx2L + 1.96728 * greyLy1L - 0.96781 * greyLy2L
                    greyLx2L = greyLx1L; greyLx1L = wL
                    greyLy2L = greyLy1L; greyLy1L = midL
                    
                    // 2. High Shelf (Fc=8000Hz, Gain=+8dB)
                    val outY_L = 1.78054 * midL - 1.33301 * greyHx1L + 0.48501 * greyHx2L + 0.25003 * greyHy1L - 0.18257 * greyHy2L
                    greyHx2L = greyHx1L; greyHx1L = midL
                    greyHy2L = greyHy1L; greyHy1L = outY_L
                    
                    outL = outY_L * 0.12 // Attenuate to avoid clipping
                    
                    val midR = 1.02268 * wR - 1.96607 * greyLx1R + 0.94636 * greyLx2R + 1.96728 * greyLy1R - 0.96781 * greyLy2R
                    greyLx2R = greyLx1R; greyLx1R = wR
                    greyLy2R = greyLy1R; greyLy1R = midR
                    
                    val outY_R = 1.78054 * midR - 1.33301 * greyHx1R + 0.48501 * greyHx2R + 0.25003 * greyHy1R - 0.18257 * greyHy2R
                    greyHx2R = greyHx1R; greyHx1R = midR
                    greyHy2R = greyHy1R; greyHy1R = outY_R
                    
                    outR = outY_R * 0.12
                }
                NoiseType.GREEN -> {
                    // Butterworth Bandpass (Fc=500Hz, Q=0.5, Fs=44100Hz)
                    val outY_L = 0.066447 * wL - 0.066447 * greenX2L + 1.862368 * greenY1L - 0.867096 * greenY2L
                    greenX2L = greenX1L; greenX1L = wL
                    greenY2L = greenY1L; greenY1L = outY_L
                    outL = outY_L * 4.0 // Gain compensation
                    
                    val outY_R = 0.066447 * wR - 0.066447 * greenX2R + 1.862368 * greenY1R - 0.867096 * greenY2R
                    greenX2R = greenX1R; greenX1R = wR
                    greenY2R = greenY1R; greenY1R = outY_R
                    outR = outY_R * 4.0
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
                NoiseType.VELVET -> {
                    velvetCounterL++
                    if (velvetCounterL >= velvetGridL) {
                        velvetCounterL = 0
                        velvetGridL = 15 + ((wL + 1.0) * 0.5 * 15.0).toInt()
                        velvetPulseL = if (wL > 0) 1.0 else -1.0
                    } else {
                        velvetPulseL = 0.0
                    }
                    outL = velvetPulseL
                    
                    velvetCounterR++
                    if (velvetCounterR >= velvetGridR) {
                        velvetCounterR = 0
                        velvetGridR = 15 + ((wR + 1.0) * 0.5 * 15.0).toInt()
                        velvetPulseR = if (wR > 0) 1.0 else -1.0
                    } else {
                        velvetPulseR = 0.0
                    }
                    outR = velvetPulseR
                }
                NoiseType.OCEAN_WAVES -> {
                    lastOceanBrownL = (lastOceanBrownL + (0.02 * wL)) / 1.02
                    lastOceanBrownR = (lastOceanBrownR + (0.02 * wR)) / 1.02
                    
                    oceanPhase += 2.0 * Math.PI / 352800.0
                    if (oceanPhase > 2.0 * Math.PI) oceanPhase -= 2.0 * Math.PI
                    val lfo = kotlin.math.sin(oceanPhase)
                    
                    val fc = 825.0 + 675.0 * lfo
                    val alpha = 2.0 * Math.PI * fc / 44100.0
                    
                    oceanLpfL += alpha * (lastOceanBrownL * 3.5 - oceanLpfL)
                    oceanLpfR += alpha * (lastOceanBrownR * 3.5 - oceanLpfR)
                    
                    val amp = 0.15 + 0.85 * (lfo * 0.5 + 0.5) * (lfo * 0.5 + 0.5)
                    
                    outL = oceanLpfL * amp
                    outR = oceanLpfR * amp
                }
                NoiseType.BINAURAL_BEATS -> {
                    binauralPhaseL += 2.0 * Math.PI * 198.0 / 44100.0
                    if (binauralPhaseL > 2.0 * Math.PI) binauralPhaseL -= 2.0 * Math.PI
                    
                    binauralPhaseR += 2.0 * Math.PI * 202.0 / 44100.0
                    if (binauralPhaseR > 2.0 * Math.PI) binauralPhaseR -= 2.0 * Math.PI
                    
                    outL = kotlin.math.sin(binauralPhaseL) * 0.5
                    outR = kotlin.math.sin(binauralPhaseR) * 0.5
                }
            }
            lastWhiteL = wL
            lastWhiteR = wR
            
            outputL = outL.toFloat()
            outputR = outR.toFloat()
        }
    }
}
