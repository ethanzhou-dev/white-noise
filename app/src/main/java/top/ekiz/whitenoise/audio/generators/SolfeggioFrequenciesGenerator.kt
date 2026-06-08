package top.ekiz.whitenoise.audio.generators

import kotlin.math.PI
import kotlin.math.sin

class SolfeggioFrequenciesGenerator : NoiseGenerator() {
    private var phase = 0.0
    private var phaseInc = 0.0

    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        val freq = 528.0
        phaseInc = 2.0 * PI * freq / sr
    }

    override fun reset() {
        phase = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        phase += phaseInc
        if (phase > 2.0 * PI) phase -= 2.0 * PI

        val output = (sin(phase) * 0.5).toFloat()

        outL = output
        outR = output
    }
}
