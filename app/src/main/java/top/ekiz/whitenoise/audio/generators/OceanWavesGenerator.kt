package top.ekiz.whitenoise.audio.generators

import kotlin.random.Random

class OceanWavesGenerator : NoiseGenerator() {
    private var oceanPhase = 0.0
    private var phaseInc = 0.0
    
    // Randomization parameters
    private var currentDuration = 6.0
    private var attackRatio = 0.4
    private var currentAmp = 1.0

    private val pink = PinkNoiseGenerator()

    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        pink.updateSampleRate(sr)
        nextWave()
    }

    override fun reset() {
        oceanPhase = 0.0
        pink.reset()
        nextWave()
    }

    private fun nextWave() {
        // The original 12.5s was too long. Natural waves are typically 4s to 8s
        currentDuration = 4.0 + Random.nextDouble() * 4.0
        phaseInc = 1.0 / (currentDuration * sampleRate)
        
        // Attack ratio between 0.3 and 0.5 (natural swell)
        attackRatio = 0.3 + Random.nextDouble() * 0.2
        
        // Amplitude variation (0.7 to 1.0)
        currentAmp = 0.7 + Random.nextDouble() * 0.3
    }

    // Asymmetric smooth wave
    private fun getEnvelope(phase: Double): Double {
        val x = if (phase < attackRatio) {
            phase / attackRatio
        } else {
            1.0 - (phase - attackRatio) / (1.0 - attackRatio)
        }
        // Smooth easing (smoothstep)
        return x * x * (3.0 - 2.0 * x)
    }

    override fun process(whiteL: Float, whiteR: Float) {
        pink.process(whiteL, whiteR)
        
        oceanPhase += phaseInc
        if (oceanPhase >= 1.0) {
            oceanPhase -= 1.0
            nextWave()
        }
        
        val smooth = getEnvelope(oceanPhase)
        
        // Map smooth (0 to 1) to volume envelope (0.1 to 1.0) scaled by currentAmp
        // Keeping minimum volume at 0.1 ensures there's always background sea rumble
        val envelope = (smooth * 0.9 + 0.1) * currentAmp
        
        outL = (pink.outL * envelope).toFloat()
        outR = (pink.outR * envelope).toFloat()
    }
}
