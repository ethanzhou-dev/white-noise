package top.ekiz.whitenoise.audio.generators

class BinauralBeatsGenerator : NoiseGenerator() {
    private var binauralPhaseL = 0.0
    private var binauralPhaseR = 0.0

    override fun reset() {
        binauralPhaseL = 0.0; binauralPhaseR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        binauralPhaseL += 2.0 * Math.PI * 198.0 / sampleRate
        if (binauralPhaseL > 2.0 * Math.PI) binauralPhaseL -= 2.0 * Math.PI
        
        binauralPhaseR += 2.0 * Math.PI * 202.0 / sampleRate
        if (binauralPhaseR > 2.0 * Math.PI) binauralPhaseR -= 2.0 * Math.PI
        
        outL = (kotlin.math.sin(binauralPhaseL) * 0.5).toFloat()
        outR = (kotlin.math.sin(binauralPhaseR) * 0.5).toFloat()
    }
}
