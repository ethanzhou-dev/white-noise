package top.ekiz.whitenoise.audio.generators

class OceanWavesGenerator : NoiseGenerator() {
    private var oceanPhase = 0.0
    private var lastPinkOutL = 0.0
    private var lastPinkOutR = 0.0
    private var phaseInc = 0.0

    private val pink = PinkNoiseGenerator()

    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        pink.updateSampleRate(sr)
        phaseInc = 0.08 / sr
    }

    override fun reset() {
        oceanPhase = 0.0
        lastPinkOutL = 0.0
        lastPinkOutR = 0.0
        pink.reset()
    }

    // Fast 0 to 1 smooth wave mimicking (sin(x)+1)/2
    private fun smoothWave(phase: Double): Double {
        val tri = if (phase < 0.5) phase * 2.0 else 2.0 - phase * 2.0
        return tri * tri * (3.0 - 2.0 * tri)
    }

    override fun process(whiteL: Float, whiteR: Float) {
        pink.process(whiteL, whiteR)
        
        oceanPhase += phaseInc
        if (oceanPhase >= 1.0) oceanPhase -= 1.0
        
        val smooth = smoothWave(oceanPhase)
        // map smooth (0 to 1) to volume envelope (0.1 to 1.0)
        val envelope = smooth * 0.9 + 0.1
        
        outL = (pink.outL * envelope).toFloat()
        outR = (pink.outR * envelope).toFloat()
    }
}
