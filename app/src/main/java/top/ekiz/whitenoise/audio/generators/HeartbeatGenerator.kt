package top.ekiz.whitenoise.audio.generators

import kotlin.math.PI
import kotlin.math.sin

class HeartbeatGenerator : NoiseGenerator() {
    private var heartPhase = 0.0
    private var heartTonePhase = 0.0
    private var phaseInc = 0.0
    private var piOverSr = 0.0

    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        phaseInc = 1.0 / sr
        piOverSr = PI / sr
    }

    override fun reset() {
        heartPhase = 0.0
        heartTonePhase = 0.0
    }

    // Fast 0 to 1 smooth wave mimicking sin(x*PI) where x in [0, 1]
    private fun fastEnv(x: Double): Double {
        return 4.0 * x * (1.0 - x)
    }

    override fun process(whiteL: Float, whiteR: Float) {
        heartPhase += phaseInc
        if (heartPhase >= 1.0) heartPhase -= 1.0
        
        var env = 0.0
        var freq = 50.0
        if (heartPhase < 0.15) { // Lub (150ms)
            val p = heartPhase / 0.15
            env = fastEnv(p)
            freq = 60.0 - p * 20.0
        } else if (heartPhase > 0.3 && heartPhase < 0.4) { // Dub (100ms)
            val p = (heartPhase - 0.3) / 0.1
            env = fastEnv(p) * 0.8
            freq = 55.0 - p * 15.0
        }
        
        heartTonePhase += freq * 2.0 * piOverSr
        if (heartTonePhase > 2.0 * PI) heartTonePhase -= 2.0 * PI
        
        val tone = (sin(heartTonePhase) * env * 0.85).toFloat()
        
        outL = tone
        outR = tone
    }
}
