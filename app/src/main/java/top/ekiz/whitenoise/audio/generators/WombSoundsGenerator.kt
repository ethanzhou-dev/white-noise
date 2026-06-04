package top.ekiz.whitenoise.audio.generators

class WombSoundsGenerator : NoiseGenerator() {
    private val heartbeat = HeartbeatGenerator()
    private val fluidNoise = DeepBrownNoiseGenerator()
    private var breathPhase = 0.0
    private var phaseInc = 0.0

    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        heartbeat.updateSampleRate(sr)
        fluidNoise.updateSampleRate(sr)
        phaseInc = 0.3 / sr
    }

    override fun reset() {
        heartbeat.reset()
        fluidNoise.reset()
        breathPhase = 0.0
    }

    // Fast 0 to 1 smooth wave mimicking (sin(x)+1)/2
    private fun smoothWave(phase: Double): Double {
        val tri = if (phase < 0.5) phase * 2.0 else 2.0 - phase * 2.0
        return tri * tri * (3.0 - 2.0 * tri)
    }

    override fun process(whiteL: Float, whiteR: Float) {
        heartbeat.process(whiteL, whiteR)
        fluidNoise.process(whiteL, whiteR)
        
        // 0.3 Hz breathing rate for volume modulation
        breathPhase += phaseInc
        if (breathPhase >= 1.0) breathPhase -= 1.0
        
        // Breath envelope from 0.6 to 1.0
        val breathEnvelope = 0.6 + 0.4 * smoothWave(breathPhase)
        
        // Mix: 70% heartbeat, 60% fluid noise (modulated by breath)
        val mixedL = (heartbeat.outL * 0.7f) + (fluidNoise.outL * 0.6f * breathEnvelope.toFloat())
        val mixedR = (heartbeat.outR * 0.7f) + (fluidNoise.outR * 0.6f * breathEnvelope.toFloat())
        
        outL = mixedL
        outR = mixedR
    }
}
