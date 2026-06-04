package top.ekiz.whitenoise.audio.generators

import kotlin.math.PI
import kotlin.math.sin

class CampfireGenerator : NoiseGenerator() {
    private var brownStateL = 0.0
    private var brownStateR = 0.0
    
    private var rumbleSvfLp1L = 0.0; private var rumbleSvfLp2L = 0.0
    private var rumbleSvfLp1R = 0.0; private var rumbleSvfLp2R = 0.0
    
    private var lfoPhase = 0.0
    private var lfoInc = 0.0

    private var crackleEnvL = 0.0; private var crackleEnvR = 0.0
    private var crackleSvfBp1L = 0.0; private var crackleSvfBp2L = 0.0
    private var crackleSvfBp1R = 0.0; private var crackleSvfBp2R = 0.0

    // Precomputed filter coefficients
    private var fRumble = 0.0
    private var fCrackle = 0.0
    
    // Fast PRNG state
    private var randomSeed = 123456789

    init {
        // Initialize constants with default sample rate
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        // Precompute filter constants using the correct SVF tuning formula
        fRumble = 2.0 * sin(PI * 150.0 / sr)
        fCrackle = 2.0 * sin(PI * 4000.0 / sr)
        // Precompute LFO phase increment (0.5 Hz)
        lfoInc = 0.5 / sr
    }

    override fun reset() {
        brownStateL = 0.0; brownStateR = 0.0
        rumbleSvfLp1L = 0.0; rumbleSvfLp2L = 0.0; rumbleSvfLp1R = 0.0; rumbleSvfLp2R = 0.0
        lfoPhase = 0.0
        crackleEnvL = 0.0; crackleEnvR = 0.0
        crackleSvfBp1L = 0.0; crackleSvfBp2L = 0.0; crackleSvfBp1R = 0.0; crackleSvfBp2R = 0.0
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
        brownStateL = (brownStateL + 0.02 * wL) / 1.02
        brownStateR = (brownStateR + 0.02 * wR) / 1.02

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
        if (randL > 0.99996f) {
            crackleEnvL = 0.5 + nextRandomFloat() * 0.4 // Occasional loud pop
        } else if (randL > 0.9998f) {
            crackleEnvL = 0.1 + nextRandomFloat() * 0.1 // Soft crackle
        }

        val randR = nextRandomFloat()
        if (randR > 0.99996f) {
            crackleEnvR = 0.5 + nextRandomFloat() * 0.4
        } else if (randR > 0.9998f) {
            crackleEnvR = 0.1 + nextRandomFloat() * 0.1
        }

        // Slightly slower decay to sound more like burning wood, less like a digital click
        crackleEnvL *= 0.995
        crackleEnvR *= 0.995

        // Mix in some brown noise for body
        val snapL = wL * 0.6 + brownStateL * 0.4
        val snapR = wR * 0.6 + brownStateR * 0.4

        // SVF lowpass on crackles (~4000Hz), damp = 1.0
        val hpCrackleL = (crackleEnvL * snapL) - crackleSvfBp1L - crackleSvfBp2L
        crackleSvfBp2L += fCrackle * hpCrackleL
        crackleSvfBp1L += fCrackle * crackleSvfBp2L
        val filteredCrackleL = crackleSvfBp1L * 0.4 // Use Lowpass output and greatly reduce volume

        val hpCrackleR = (crackleEnvR * snapR) - crackleSvfBp1R - crackleSvfBp2R
        crackleSvfBp2R += fCrackle * hpCrackleR
        crackleSvfBp1R += fCrackle * crackleSvfBp2R
        val filteredCrackleR = crackleSvfBp1R * 0.4

        // 3. Combine
        outL = (rumbleL + filteredCrackleL).toFloat().coerceIn(-1.0f, 1.0f)
        outR = (rumbleR + filteredCrackleR).toFloat().coerceIn(-1.0f, 1.0f)
    }
}
