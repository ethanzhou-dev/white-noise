package top.ekiz.whitenoise.audio.generators

import kotlin.math.PI
import kotlin.math.sin

class BinauralBeatsGenerator : NoiseGenerator() {
    private var binauralPhaseL = 0.0
    private var binauralPhaseR = 0.0
    private var phaseIncL = 0.0
    private var phaseIncR = 0.0

    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        val carrier = 200.0
        val beat = 10.0
        phaseIncL = 2.0 * PI * (carrier - beat / 2.0) / sr
        phaseIncR = 2.0 * PI * (carrier + beat / 2.0) / sr
    }

    override fun reset() {
        binauralPhaseL = 0.0
        binauralPhaseR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        binauralPhaseL += phaseIncL
        binauralPhaseR += phaseIncR
        
        if (binauralPhaseL > 2.0 * PI) binauralPhaseL -= 2.0 * PI
        if (binauralPhaseR > 2.0 * PI) binauralPhaseR -= 2.0 * PI
        
        outL = (sin(binauralPhaseL) * 0.5).toFloat()
        outR = (sin(binauralPhaseR) * 0.5).toFloat()
    }
}
