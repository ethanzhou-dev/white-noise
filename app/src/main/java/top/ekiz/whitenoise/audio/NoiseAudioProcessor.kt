package top.ekiz.whitenoise.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import top.ekiz.whitenoise.NoiseType
import top.ekiz.whitenoise.audio.generators.NoiseGenerator
import top.ekiz.whitenoise.audio.generators.NoiseGeneratorFactory
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
    
    private var currentGenerator: NoiseGenerator = NoiseGeneratorFactory.create(noiseType)
    private var fadingGenerator: NoiseGenerator? = null

    fun initialize(type: NoiseType) {
        noiseType = type
        currentNoiseType = type
        fadingGenerator = null
        crossfadeProgress = 1f
        forceImmediateSwitch = false
        
        currentGenerator = NoiseGeneratorFactory.create(type)
        if (inputAudioFormat.sampleRate > 0) {
            currentGenerator.updateSampleRate(inputAudioFormat.sampleRate.toDouble())
        }
    }
    
    private var crossfadeProgress = 1f
    private var crossfadeStep = 1f / (44100f * 1.5f) // 1.5s crossfade

    // Spatial Audio (Binaural) state
    private var lfoPhase: Double = 0.0
    private var lfoStep: Double = 2.0 * Math.PI * 0.05 / 44100.0 // 0.05 Hz
    private var headShadowL = 0f
    private var headShadowR = 0f

    private var rngState: Long = System.nanoTime().let { if (it == 0L) 1L else it }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val sr = inputAudioFormat.sampleRate
        if (sr > 0) {
            crossfadeStep = 1f / (sr.toFloat() * 1.5f)
            lfoStep = 2.0 * Math.PI * 0.05 / sr.toDouble()
            currentGenerator.updateSampleRate(sr.toDouble())
            fadingGenerator?.updateSampleRate(sr.toDouble())
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        super.onFlush()
        currentGenerator.reset()
        fadingGenerator?.reset()
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
                fadingGenerator = null
                crossfadeProgress = 1f
                currentGenerator = NoiseGeneratorFactory.create(noiseType)
                if (inputAudioFormat.sampleRate > 0) {
                    currentGenerator.updateSampleRate(inputAudioFormat.sampleRate.toDouble())
                }
                forceImmediateSwitch = false
            } else {
                fadingGenerator = currentGenerator
                currentGenerator = NoiseGeneratorFactory.create(noiseType)
                if (inputAudioFormat.sampleRate > 0) {
                    currentGenerator.updateSampleRate(inputAudioFormat.sampleRate.toDouble())
                }
                
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

            // XorShift64 algorithm for ultra-fast pseudo-random noise
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
            
            currentGenerator.process(whiteL, whiteR)
            var outputL = currentGenerator.outL
            var outputR = currentGenerator.outR

            if (crossfadeProgress < 1f) {
                crossfadeProgress += crossfadeStep
                if (crossfadeProgress >= 1f) {
                    crossfadeProgress = 1f
                    fadingGenerator = null
                } else {
                    val fadeGen = fadingGenerator
                    if (fadeGen != null) {
                        fadeGen.process(whiteL, whiteR)
                        // Linear crossfade since both states share the same white noise source (highly correlated)
                        val fadeInGain = crossfadeProgress
                        val fadeOutGain = 1f - crossfadeProgress
                        
                        outputL = outputL * fadeInGain + fadeGen.outL * fadeOutGain
                        outputR = outputR * fadeInGain + fadeGen.outR * fadeOutGain
                    }
                }
            }

            // Binaural Spatializer
            if (isSpatialAudioEnabled && isStereo) {
                lfoPhase += lfoStep
                if (lfoPhase > Math.PI * 2.0) {
                    lfoPhase -= Math.PI * 2.0
                }
                
                val lfoVal = kotlin.math.sin(lfoPhase).toFloat()
                
                val levelL = 1.0f - max(0f, lfoVal * 0.25f)
                val levelR = 1.0f - max(0f, -lfoVal * 0.25f)
                
                val lpfCoeffL = 1.0f - max(0f, lfoVal * 0.4f)
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
}
