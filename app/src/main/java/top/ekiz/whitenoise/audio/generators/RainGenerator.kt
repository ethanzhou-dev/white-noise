package top.ekiz.whitenoise.audio.generators

import kotlin.random.Random

class RainGenerator : NoiseGenerator() {
    private var pinkB0 = 0.0; private var pinkB1 = 0.0; private var pinkB2 = 0.0
    private var pinkB3 = 0.0; private var pinkB4 = 0.0; private var pinkB5 = 0.0; private var pinkB6 = 0.0

    private var hissLpSvf1 = 0.0; private var hissLpSvf2 = 0.0
    
    private var dropEnvL = 0.0; private var dropEnvR = 0.0
    private var dropBp1L = 0.0; private var dropBp2L = 0.0
    private var dropBp1R = 0.0; private var dropBp2R = 0.0

    override fun reset() {
        pinkB0 = 0.0; pinkB1 = 0.0; pinkB2 = 0.0; pinkB3 = 0.0; pinkB4 = 0.0; pinkB5 = 0.0; pinkB6 = 0.0
        hissLpSvf1 = 0.0; hissLpSvf2 = 0.0
        dropEnvL = 0.0; dropEnvR = 0.0
        dropBp1L = 0.0; dropBp2L = 0.0; dropBp1R = 0.0; dropBp2R = 0.0
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
        val normalizedPink = pinkOut * 0.11
        
        // 2. Lowpass filter the pink noise for distant rain (SVF ~1200Hz)
        val piOverSr = Math.PI / sampleRate
        val fHiss = 2.0 * (1200.0 * piOverSr)
        val dampHiss = 1.0
        val hp = normalizedPink - hissLpSvf1 - dampHiss * hissLpSvf2
        hissLpSvf2 += fHiss * hp
        hissLpSvf1 += fHiss * hissLpSvf2
        val backgroundRain = hissLpSvf1 * 0.5

        // 3. Generate Rain Drops
        // Left channel drops
        if (Random.nextDouble() > 0.9997) {
            dropEnvL = 1.0 + Random.nextDouble() * 0.5
        }
        // Right channel drops
        if (Random.nextDouble() > 0.9997) {
            dropEnvR = 1.0 + Random.nextDouble() * 0.5
        }

        // Decay envelope (approx 10-20ms)
        dropEnvL *= 0.998
        dropEnvR *= 0.998

        val rawDropL = dropEnvL * wL
        val rawDropR = dropEnvR * wR

        // Bandpass filter for the drops to sound like hitting surfaces (~2500Hz)
        val fDrop = 2.0 * (2500.0 * piOverSr)
        val dampDrop = 1.5 // moderate damping

        val hpDropL = rawDropL - dropBp1L - dampDrop * dropBp2L
        dropBp2L += fDrop * hpDropL
        dropBp1L += fDrop * dropBp2L
        val filteredDropL = dropBp2L * 2.0 // Boost drop volume

        val hpDropR = rawDropR - dropBp1R - dampDrop * dropBp2R
        dropBp2R += fDrop * hpDropR
        dropBp1R += fDrop * dropBp2R
        val filteredDropR = dropBp2R * 2.0

        // 4. Combine
        outL = (backgroundRain + filteredDropL).toFloat().coerceIn(-1.0f, 1.0f)
        outR = (backgroundRain + filteredDropR).toFloat().coerceIn(-1.0f, 1.0f)
    }
}
