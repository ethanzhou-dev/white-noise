package top.ekiz.whitenoise.audio.generators

import kotlin.math.PI

class RainGenerator : NoiseGenerator() {
    private var pinkB0 = 0.0; private var pinkB1 = 0.0; private var pinkB2 = 0.0
    private var pinkB3 = 0.0; private var pinkB4 = 0.0; private var pinkB5 = 0.0; private var pinkB6 = 0.0

    private var lp1L = 0.0; private var lp2L = 0.0
    private var lp1R = 0.0; private var lp2R = 0.0

    private var gustLfoPhase = 0.0
    private var splatterLfoPhaseL = 0.0
    private var splatterLfoPhaseR = 0.0

    private var piOverSr = 0.0
    private var gustLfoInc = 0.0
    private var splatterLfoIncL = 0.0
    private var splatterLfoIncR = 0.0
    
    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        piOverSr = PI / sr
        gustLfoInc = 0.05 / sr
        splatterLfoIncL = 20.0 / sr
        splatterLfoIncR = 22.0 / sr
    }

    override fun reset() {
        pinkB0 = 0.0; pinkB1 = 0.0; pinkB2 = 0.0; pinkB3 = 0.0; pinkB4 = 0.0; pinkB5 = 0.0; pinkB6 = 0.0
        lp1L = 0.0; lp2L = 0.0; lp1R = 0.0; lp2R = 0.0
        gustLfoPhase = 0.0; splatterLfoPhaseL = 0.0; splatterLfoPhaseR = 0.0
    }

    // For fast phase wrap (-0.5 to 1.5 safely maps to 0 to 1)
    private fun wrapPhase(phase: Double): Double {
        var p = phase
        if (p >= 1.0) p -= 1.0
        else if (p < 0.0) p += 1.0
        return p
    }

    // Fast 0 to 1 smooth wave (polynomial approximation mimicking sin(x)*0.5+0.5)
    private fun smoothWave(phase: Double): Double {
        val tri = if (phase < 0.5) phase * 2.0 else 2.0 - phase * 2.0
        return tri * tri * (3.0 - 2.0 * tri)
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()
        val wMono = (wL + wR) * 0.5
        
        // 1. Generate Pink Noise base for the "hiss" of rain
        pinkB0 = 0.99886 * pinkB0 + wMono * 0.0555179
        pinkB1 = 0.99332 * pinkB1 + wMono * 0.0750759
        pinkB2 = 0.96900 * pinkB2 + wMono * 0.1538520
        pinkB3 = 0.86650 * pinkB3 + wMono * 0.3104856
        pinkB4 = 0.55000 * pinkB4 + wMono * 0.5329522
        pinkB5 = -0.7616 * pinkB5 - wMono * 0.0168980
        val pinkOut = pinkB0 + pinkB1 + pinkB2 + pinkB3 + pinkB4 + pinkB5 + pinkB6 + wMono * 0.5362
        pinkB6 = wMono * 0.115926
        val pink = pinkOut * 0.11

        // 2. Wind Gust LFO (0.05 Hz)
        gustLfoPhase += gustLfoInc
        if (gustLfoPhase >= 1.0) gustLfoPhase -= 1.0
        val gust = smoothWave(gustLfoPhase) // 0.0 to 1.0
        
        // 3. Splatter LFOs (chaotic granular texture, ~20 Hz)
        splatterLfoPhaseL += splatterLfoIncL
        if (splatterLfoPhaseL >= 1.0) splatterLfoPhaseL -= 1.0
        // Phase modulation by white noise: wL * 2.0 rads maps to wL * 0.3183 cycles
        val pL = wrapPhase(splatterLfoPhaseL + wL * 0.3183) 
        val splatterL = smoothWave(pL)

        splatterLfoPhaseR += splatterLfoIncR
        if (splatterLfoPhaseR >= 1.0) splatterLfoPhaseR -= 1.0
        val pR = wrapPhase(splatterLfoPhaseR + wR * 0.3183)
        val splatterR = smoothWave(pR)

        // 4. Main Rain Body (Dynamic Lowpass on Pink Noise)
        // Cutoff shifts between 800Hz (distant/calm) and 4500Hz (close/windy)
        val cutoff = (800.0 + gust * 3700.0).coerceAtMost(sampleRate * 0.45)
        val fLp = 2.0 * cutoff * piOverSr // Linear approximation is fast and sufficient here
        
        val hpL = pink - lp1L - 1.2 * lp2L
        lp2L += fLp * hpL
        lp1L += fLp * lp2L
        val bodyL = lp1L

        val hpR = pink - lp1R - 1.2 * lp2R
        lp2R += fLp * hpR
        lp1R += fLp * lp2R
        val bodyR = lp1R

        // 5. Close Splash (White noise heavily modulated by chaotic splatters)
        // We add some gust influence so splashing is louder during wind
        val splashIntensity = 0.05 + gust * 0.2
        val splashL = wL * splatterL * splashIntensity
        val splashR = wR * splatterR * splashIntensity

        // 6. Combine
        outL = (bodyL * 0.8 + splashL).toFloat().coerceIn(-1.0f, 1.0f)
        outR = (bodyR * 0.8 + splashR).toFloat().coerceIn(-1.0f, 1.0f)
    }
}
