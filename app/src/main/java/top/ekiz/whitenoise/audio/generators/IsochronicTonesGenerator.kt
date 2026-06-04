package top.ekiz.whitenoise.audio.generators

import kotlin.math.sin

class IsochronicTonesGenerator : NoiseGenerator() {
    private var carrierPhase = 0.0
    private var modPhase = 0.0

    override fun reset() {
        carrierPhase = 0.0
        modPhase = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val carrierFreq = 200.0
        val modFreq = 10.0

        carrierPhase += 2.0 * Math.PI * carrierFreq / sampleRate
        if (carrierPhase > 2.0 * Math.PI) carrierPhase -= 2.0 * Math.PI
        
        modPhase += 2.0 * Math.PI * modFreq / sampleRate
        if (modPhase > 2.0 * Math.PI) modPhase -= 2.0 * Math.PI
        
        // Raised sine wave for smooth modulation: (sin(phase) + 1) / 2
        // ranges from 0 to 1
        val envelope = (sin(modPhase) + 1.0) / 2.0
        
        // Output sine wave amplitude modulated by envelope
        // Base sine wave max amplitude is 0.5 to prevent too loud output
        val output = (sin(carrierPhase) * 0.5 * envelope).toFloat()
        
        outL = output
        outR = output
    }
}
