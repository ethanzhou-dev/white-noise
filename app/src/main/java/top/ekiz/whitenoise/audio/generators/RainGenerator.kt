package top.ekiz.whitenoise.audio.generators

import kotlin.math.sin

class RainGenerator : NoiseGenerator() {
    private var pinkB0 = 0.0; private var pinkB1 = 0.0; private var pinkB2 = 0.0
    private var pinkB3 = 0.0; private var pinkB4 = 0.0; private var pinkB5 = 0.0; private var pinkB6 = 0.0

    private var lp1L = 0.0; private var lp2L = 0.0
    private var lp1R = 0.0; private var lp2R = 0.0

    private var gustLfoPhase = 0.0
    private var splatterLfoPhaseL = 0.0
    private var splatterLfoPhaseR = 0.0

    override fun reset() {
        pinkB0 = 0.0; pinkB1 = 0.0; pinkB2 = 0.0; pinkB3 = 0.0; pinkB4 = 0.0; pinkB5 = 0.0; pinkB6 = 0.0
        lp1L = 0.0; lp2L = 0.0; lp1R = 0.0; lp2R = 0.0
        gustLfoPhase = 0.0; splatterLfoPhaseL = 0.0; splatterLfoPhaseR = 0.0
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

        val piOverSr = Math.PI / sampleRate

        // 2. Wind Gust LFO (0.05 Hz)
        gustLfoPhase += 2.0 * Math.PI * 0.05 / sampleRate
        if (gustLfoPhase > 2.0 * Math.PI) gustLfoPhase -= 2.0 * Math.PI
        val gust = sin(gustLfoPhase) * 0.5 + 0.5 // 0.0 to 1.0
        
        // 3. Splatter LFOs (chaotic granular texture, ~20 Hz)
        splatterLfoPhaseL += 2.0 * Math.PI * 20.0 / sampleRate
        if (splatterLfoPhaseL > 2.0 * Math.PI) splatterLfoPhaseL -= 2.0 * Math.PI
        val splatterL = sin(splatterLfoPhaseL + wL * 2.0) * 0.5 + 0.5

        splatterLfoPhaseR += 2.0 * Math.PI * 22.0 / sampleRate
        if (splatterLfoPhaseR > 2.0 * Math.PI) splatterLfoPhaseR -= 2.0 * Math.PI
        val splatterR = sin(splatterLfoPhaseR + wR * 2.0) * 0.5 + 0.5

        // 4. Main Rain Body (Dynamic Lowpass on Pink Noise)
        // Cutoff shifts between 800Hz (distant/calm) and 4500Hz (close/windy)
        val cutoff = 800.0 + gust * 3700.0
        val fLp = 2.0 * (cutoff * piOverSr)
        val damp = 1.2

        val hpL = pink - lp1L - damp * lp2L
        lp2L += fLp * hpL
        lp1L += fLp * lp2L
        val bodyL = lp1L

        val hpR = pink - lp1R - damp * lp2R
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
