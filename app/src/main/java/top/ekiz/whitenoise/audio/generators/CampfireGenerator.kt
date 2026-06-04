package top.ekiz.whitenoise.audio.generators

import kotlin.math.sin
import kotlin.random.Random

class CampfireGenerator : NoiseGenerator() {
    private var brownStateL = 0.0
    private var brownStateR = 0.0
    
    private var rumbleSvfLp1L = 0.0; private var rumbleSvfLp2L = 0.0
    private var rumbleSvfLp1R = 0.0; private var rumbleSvfLp2R = 0.0
    
    private var lfoPhase = 0.0

    private var crackleEnvL = 0.0; private var crackleEnvR = 0.0
    private var crackleSvfBp1L = 0.0; private var crackleSvfBp2L = 0.0
    private var crackleSvfBp1R = 0.0; private var crackleSvfBp2R = 0.0

    override fun reset() {
        brownStateL = 0.0; brownStateR = 0.0
        rumbleSvfLp1L = 0.0; rumbleSvfLp2L = 0.0; rumbleSvfLp1R = 0.0; rumbleSvfLp2R = 0.0
        lfoPhase = 0.0
        crackleEnvL = 0.0; crackleEnvR = 0.0
        crackleSvfBp1L = 0.0; crackleSvfBp2L = 0.0; crackleSvfBp1R = 0.0; crackleSvfBp2R = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        // 1. Rumble (Deep fire burning) using Brown noise
        brownStateL = (brownStateL + 0.02 * wL) / 1.02
        brownStateR = (brownStateR + 0.02 * wR) / 1.02

        // LFO for fire "breathing" effect (approx 0.5 Hz)
        val piOverSr = Math.PI / sampleRate
        lfoPhase += 2.0 * Math.PI * 0.5 / sampleRate
        if (lfoPhase > 2.0 * Math.PI) lfoPhase -= 2.0 * Math.PI
        val lfo = (sin(lfoPhase) * 0.5 + 0.5) * 0.4 + 0.6 // Range 0.6 to 1.0

        // SVF lowpass on brown noise (~150Hz)
        val fRumble = 2.0 * (150.0 * piOverSr)
        val dampRumble = 1.0

        val hpRumbleL = brownStateL * lfo * 5.0 - rumbleSvfLp1L - dampRumble * rumbleSvfLp2L
        rumbleSvfLp2L += fRumble * hpRumbleL
        rumbleSvfLp1L += fRumble * rumbleSvfLp2L
        val rumbleL = rumbleSvfLp1L

        val hpRumbleR = brownStateR * lfo * 5.0 - rumbleSvfLp1R - dampRumble * rumbleSvfLp2R
        rumbleSvfLp2R += fRumble * hpRumbleR
        rumbleSvfLp1R += fRumble * rumbleSvfLp2R
        val rumbleR = rumbleSvfLp1R

        // 2. Crackles (Popping wood)
        if (Random.nextDouble() > 0.99996) {
            crackleEnvL = 0.5 + Random.nextDouble() * 0.4 // Occasional loud pop
        } else if (Random.nextDouble() > 0.9998) {
            crackleEnvL = 0.1 + Random.nextDouble() * 0.1 // Soft crackle
        }

        if (Random.nextDouble() > 0.99996) {
            crackleEnvR = 0.5 + Random.nextDouble() * 0.4
        } else if (Random.nextDouble() > 0.9998) {
            crackleEnvR = 0.1 + Random.nextDouble() * 0.1
        }

        // Slightly slower decay to sound more like burning wood, less like a digital click
        crackleEnvL *= 0.995
        crackleEnvR *= 0.995

        // Use lowpass filter instead of bandpass to give the crackle more body and warmth
        val fCrackle = 2.0 * (4000.0 * piOverSr)
        val dampCrackle = 1.0

        // Mix in some brown noise for body
        val snapL = wL * 0.6 + brownStateL * 0.4
        val snapR = wR * 0.6 + brownStateR * 0.4

        val hpCrackleL = (crackleEnvL * snapL) - crackleSvfBp1L - dampCrackle * crackleSvfBp2L
        crackleSvfBp2L += fCrackle * hpCrackleL
        crackleSvfBp1L += fCrackle * crackleSvfBp2L
        val filteredCrackleL = crackleSvfBp1L * 0.4 // Use Lowpass output and greatly reduce volume

        val hpCrackleR = (crackleEnvR * snapR) - crackleSvfBp1R - dampCrackle * crackleSvfBp2R
        crackleSvfBp2R += fCrackle * hpCrackleR
        crackleSvfBp1R += fCrackle * crackleSvfBp2R
        val filteredCrackleR = crackleSvfBp1R * 0.4

        // 3. Combine
        outL = (rumbleL + filteredCrackleL).toFloat().coerceIn(-1.0f, 1.0f)
        outR = (rumbleR + filteredCrackleR).toFloat().coerceIn(-1.0f, 1.0f)
    }
}
