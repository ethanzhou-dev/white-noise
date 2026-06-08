package top.ekiz.whitenoise.audio.generators

import kotlin.random.Random

class OceanWavesGenerator : NoiseGenerator() {
    private var oceanPhase = 0.0
    private var phaseInc = 0.0
    
    
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
        
        currentDuration = 4.0 + Random.nextDouble() * 4.0
        phaseInc = 1.0 / (currentDuration * sampleRate)
        
        
        attackRatio = 0.3 + Random.nextDouble() * 0.2
        
        
        currentAmp = 0.7 + Random.nextDouble() * 0.3
    }

    
    private fun getEnvelope(phase: Double): Double {
        val x = if (phase < attackRatio) {
            phase / attackRatio
        } else {
            1.0 - (phase - attackRatio) / (1.0 - attackRatio)
        }
        
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
        
        
        
        val envelope = (smooth * 0.9 + 0.1) * currentAmp
        
        outL = (pink.outL * envelope).toFloat()
        outR = (pink.outR * envelope).toFloat()
    }
}
