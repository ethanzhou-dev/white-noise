package top.ekiz.whitenoise.audio.generators

class OceanWavesGenerator : NoiseGenerator() {
    private var oceanPhase = 0.0
    private var oceanLpfL = 0.0
    private var oceanLpfR = 0.0
    private var lastOceanBrownL = 0.0
    private var lastOceanBrownR = 0.0

    override fun reset() {
        oceanPhase = 0.0; oceanLpfL = 0.0; oceanLpfR = 0.0; lastOceanBrownL = 0.0; lastOceanBrownR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        lastOceanBrownL = (lastOceanBrownL + (0.02 * wL)) / 1.02
        lastOceanBrownR = (lastOceanBrownR + (0.02 * wR)) / 1.02
        
        oceanPhase += 2.0 * Math.PI / (8.0 * sampleRate)
        if (oceanPhase > 2.0 * Math.PI) oceanPhase -= 2.0 * Math.PI
        val lfo = kotlin.math.sin(oceanPhase)
        
        val fc = 825.0 + 675.0 * lfo
        val alpha = 2.0 * Math.PI * fc / sampleRate
        
        oceanLpfL += alpha * (lastOceanBrownL * 3.5 - oceanLpfL)
        oceanLpfR += alpha * (lastOceanBrownR * 3.5 - oceanLpfR)
        
        val amp = 0.15 + 0.85 * (lfo * 0.5 + 0.5) * (lfo * 0.5 + 0.5)
        
        outL = (oceanLpfL * amp).toFloat()
        outR = (oceanLpfR * amp).toFloat()
    }
}
