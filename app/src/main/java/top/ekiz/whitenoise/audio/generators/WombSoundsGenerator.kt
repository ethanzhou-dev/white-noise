package top.ekiz.whitenoise.audio.generators

import kotlin.math.sin

class WombSoundsGenerator : NoiseGenerator() {
    private val heartbeat = HeartbeatGenerator()
    private val fluidNoise = DeepBrownNoiseGenerator()
    private var breathPhase = 0.0

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        heartbeat.updateSampleRate(sr)
        fluidNoise.updateSampleRate(sr)
    }

    override fun reset() {
        heartbeat.reset()
        fluidNoise.reset()
        breathPhase = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        heartbeat.process(whiteL, whiteR)
        fluidNoise.process(whiteL, whiteR)
        
        // 0.3 Hz breathing rate for volume modulation
        breathPhase += 2.0 * Math.PI * 0.3 / sampleRate
        if (breathPhase > 2.0 * Math.PI) breathPhase -= 2.0 * Math.PI
        
        // Breath envelope from 0.6 to 1.0
        val breathEnvelope = 0.6 + 0.4 * ((sin(breathPhase) + 1.0) / 2.0)
        
        // Mix: 70% heartbeat, 60% fluid noise (modulated by breath)
        val mixedL = (heartbeat.outL * 0.7f) + (fluidNoise.outL * 0.6f * breathEnvelope.toFloat())
        val mixedR = (heartbeat.outR * 0.7f) + (fluidNoise.outR * 0.6f * breathEnvelope.toFloat())
        
        // Apply a general low-pass filter effect by reducing the overall high frequencies
        // Actually, DeepBrown is already very low-pass.
        outL = mixedL
        outR = mixedR
    }
}
