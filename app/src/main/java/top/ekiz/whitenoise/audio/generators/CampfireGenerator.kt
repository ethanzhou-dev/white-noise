package top.ekiz.whitenoise.audio.generators

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class CampfireGenerator : NoiseGenerator() {
    private var brownStateL = 0.0
    private var brownStateR = 0.0
    
    private var rumbleSvfLp1L = 0.0; private var rumbleSvfLp2L = 0.0
    private var rumbleSvfLp1R = 0.0; private var rumbleSvfLp2R = 0.0
    
    private var lfoPhase = 0.0
    private var lfoInc = 0.0

    private var crackleEnvL = 0.0; private var crackleEnvR = 0.0
    private var crackleSvfLpL = 0.0; private var crackleSvfBpL = 0.0
    private var crackleSvfLpR = 0.0; private var crackleSvfBpR = 0.0

    // Precomputed filter coefficients
    private var fRumble = 0.0
    private var fCrackle = 0.0
    
    // Sample-rate independent constants
    private var crackleDecay = 0.0
    private var brownIntegrationAlpha = 0.0
    private var brownIntegrationBeta = 0.0
    private var loudPopThreshold = 0.0f
    private var softPopThreshold = 0.0f

    // Fast PRNG state
    private var randomSeed = 123456789

    init {
        // Initialize constants with default sample rate
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        
        // 1. SVF filter stability protection (Nyquist limit clamping)
        val safeRumbleFreq = 150.0.coerceAtMost(sr * 0.45)
        val safeCrackleFreq = 4000.0.coerceAtMost(sr * 0.45)
        
        fRumble = 2.0 * sin(PI * safeRumbleFreq / sr)
        fCrackle = 2.0 * sin(PI * safeCrackleFreq / sr)
        
        // 2. Precompute LFO phase increment (0.5 Hz)
        lfoInc = 0.5 / sr
        
        // 3. Sample-rate independent decay (target: ~4.5ms time constant)
        crackleDecay = exp(-1.0 / (0.0045 * sr))
        
        // 4. Sample-rate independent Brown noise integrator (~139Hz cutoff, matches original 1.02 pole)
        val brownPole = exp(-2.0 * PI * 139.0 / sr)
        brownIntegrationAlpha = 1.0 - brownPole
        brownIntegrationBeta = brownPole
        
        // 5. Sample-rate independent probability thresholds
        val loudPopsPerSec = 1.764f
        val softPopsPerSec = 8.82f
        loudPopThreshold = 1.0f - (loudPopsPerSec / sr).toFloat()
        softPopThreshold = 1.0f - (softPopsPerSec / sr).toFloat()
    }

    override fun reset() {
        brownStateL = 0.0; brownStateR = 0.0
        rumbleSvfLp1L = 0.0; rumbleSvfLp2L = 0.0; rumbleSvfLp1R = 0.0; rumbleSvfLp2R = 0.0
        lfoPhase = 0.0
        crackleEnvL = 0.0; crackleEnvR = 0.0
        crackleSvfLpL = 0.0; crackleSvfBpL = 0.0; crackleSvfLpR = 0.0; crackleSvfBpR = 0.0
    }

    // Fast inline pseudo-random number generator (LCG)
    private fun nextRandomFloat(): Float {
        randomSeed = randomSeed * 1664525 + 1013904223
        return ((randomSeed ushr 8) and 0xFFFFFF) / 16777216f
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        // 1. Rumble (Deep fire burning) using Brown noise
        brownStateL = brownStateL * brownIntegrationBeta + wL * brownIntegrationAlpha
        brownStateR = brownStateR * brownIntegrationBeta + wR * brownIntegrationAlpha

        // LFO for fire "breathing" effect (approx 0.5 Hz)
        lfoPhase += lfoInc
        if (lfoPhase >= 1.0) lfoPhase -= 1.0
        
        // Fast approximation of sine wave (smoothstep triangle) instead of Math.sin
        val tri = if (lfoPhase < 0.5) lfoPhase * 2.0 else 2.0 - lfoPhase * 2.0
        val smoothLfo = tri * tri * (3.0 - 2.0 * tri)
        val lfo = smoothLfo * 0.4 + 0.6 // Range 0.6 to 1.0
        val rumbleAmp = lfo * 5.0

        // SVF lowpass on brown noise (~150Hz), damp = 1.0
        val hpRumbleL = brownStateL * rumbleAmp - rumbleSvfLp1L - rumbleSvfLp2L
        rumbleSvfLp2L += fRumble * hpRumbleL
        rumbleSvfLp1L += fRumble * rumbleSvfLp2L
        val rumbleL = rumbleSvfLp1L

        val hpRumbleR = brownStateR * rumbleAmp - rumbleSvfLp1R - rumbleSvfLp2R
        rumbleSvfLp2R += fRumble * hpRumbleR
        rumbleSvfLp1R += fRumble * rumbleSvfLp2R
        val rumbleR = rumbleSvfLp1R

        // 2. Crackles (Popping wood)
        val randL = nextRandomFloat()
        if (randL > loudPopThreshold) {
            crackleEnvL = 0.5 + nextRandomFloat() * 0.4 // Occasional loud pop
        } else if (randL > softPopThreshold) {
            crackleEnvL = 0.1 + nextRandomFloat() * 0.1 // Soft crackle
        }

        val randR = nextRandomFloat()
        if (randR > loudPopThreshold) {
            crackleEnvR = 0.5 + nextRandomFloat() * 0.4
        } else if (randR > softPopThreshold) {
            crackleEnvR = 0.1 + nextRandomFloat() * 0.1
        }

        // Slightly slower decay to sound more like burning wood, less like a digital click
        crackleEnvL *= crackleDecay
        crackleEnvR *= crackleDecay

        // Mix in some brown noise for body
        val snapL = wL * 0.6 + brownStateL * 0.4
        val snapR = wR * 0.6 + brownStateR * 0.4

        // SVF lowpass on crackles (~4000Hz), damp = 1.0
        val hpCrackleL = (crackleEnvL * snapL) - crackleSvfLpL - crackleSvfBpL
        crackleSvfBpL += fCrackle * hpCrackleL
        crackleSvfLpL += fCrackle * crackleSvfBpL
        val filteredCrackleL = crackleSvfLpL * 0.4 // Use Lowpass output and greatly reduce volume

        val hpCrackleR = (crackleEnvR * snapR) - crackleSvfLpR - crackleSvfBpR
        crackleSvfBpR += fCrackle * hpCrackleR
        crackleSvfLpR += fCrackle * crackleSvfBpR
        val filteredCrackleR = crackleSvfLpR * 0.4

        // 3. Combine
        outL = (rumbleL + filteredCrackleL).toFloat().coerceIn(-1.0f, 1.0f)
        outR = (rumbleR + filteredCrackleR).toFloat().coerceIn(-1.0f, 1.0f)
    }
}
