package top.ekiz.whitenoise.audio.generators

import kotlin.math.sin

class SolfeggioFrequenciesGenerator : NoiseGenerator() {
    private var phase = 0.0
    
    override fun reset() {
        phase = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        // 528 Hz is the Love / DNA repair frequency
        val freq = 528.0
        
        phase += 2.0 * Math.PI * freq / sampleRate
        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
        
        // Pure sine wave at 528 Hz, amplitude 0.5
        val output = (sin(phase) * 0.5).toFloat()
        
        outL = output
        outR = output
    }
}
